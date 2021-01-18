/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.messaging;

import com.radixdlt.consensus.HashSigner;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.radixdlt.crypto.Hasher;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.streams.RoundRobinBackpressuredProcessor;
import io.reactivex.rxjava3.core.Flowable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.SystemMessage;
import org.radix.utils.SimpleThreadPool;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Inject;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithTransport;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.serialization.Serialization;

final class MessageCentralImpl implements MessageCentral {
	private static final Logger log = LogManager.getLogger();

	// Dependencies
	private final TransportManager connectionManager;
	private final AddressBook addressBook;
	private final SystemCounters counters;

	// Message dispatching
	private final MessageDispatcher messageDispatcher;
	private final MessagePreprocessor messagePreprocessor;

	// Our time base for System.nanoTime() differences.  Per documentation can only compare deltas
	private final long timeBase = System.nanoTime();

	// Listeners we are managing
	private final List<Transport> transports;

	private final RateLimiter outboundLogRateLimiter = RateLimiter.create(1.0);

	// Inbound message handling
	private final RoundRobinBackpressuredProcessor<InboundMessage> inboundMessageProcessor =
		new RoundRobinBackpressuredProcessor<>();

	private final RoundRobinBackpressuredProcessor<Pair<Peer, Message>> peerMessageProcessor =
		new RoundRobinBackpressuredProcessor<>();

	// Outbound message handling
	private final SimpleBlockingQueue<OutboundMessageEvent> outboundQueue;
	private final SimpleThreadPool<OutboundMessageEvent> outboundThreadPool;

	@Inject
	MessageCentralImpl(
		MessageCentralConfiguration config,
		Serialization serialization,
		TransportManager transportManager,
		AddressBook addressBook,
		TimeSupplier timeSource,
		EventQueueFactory<OutboundMessageEvent> outboundEventQueueFactory,
		LocalSystem localSystem,
		SystemCounters counters,
		Hasher hasher,
		HashSigner hashSigner
	) {
		this.counters = Objects.requireNonNull(counters);
		this.outboundQueue = outboundEventQueueFactory.createEventQueue(
			config.messagingOutboundQueueMax(16384),
			OutboundMessageEvent.comparator()
		);

		this.connectionManager = Objects.requireNonNull(transportManager);
		this.addressBook = Objects.requireNonNull(addressBook);

		Objects.requireNonNull(timeSource);
		Objects.requireNonNull(serialization);

		this.messageDispatcher = new MessageDispatcher(
			counters,
			config,
			serialization,
			timeSource,
			hasher,
			hashSigner
		);

		this.messagePreprocessor = new MessagePreprocessor(
			counters,
			config,
			timeSource,
			localSystem,
			this.addressBook,
			hasher,
			serialization
		);

		this.transports = Lists.newArrayList(transportManager.transports());

		// Start outbound processing thread
		this.outboundThreadPool = new SimpleThreadPool<>(
			"Outbound message processing",
			1, // Ensure messages sent in-order
			outboundQueue::take,
			this::outboundMessageProcessor,
			log
		);
		this.outboundThreadPool.start();

		// Start our listeners
		this.transports
			.stream().map(Transport::start)
			.forEach(inboundMessageProcessor::subscribeTo);

		setupMessageProcessor();
	}

	private void setupMessageProcessor() {
		final var processedMessages =
			Flowable.fromPublisher(inboundMessageProcessor)
				.map(this.messagePreprocessor::process)
				.filter(Optional::isPresent)
				.map(Optional::get);

		peerMessageProcessor.subscribeTo(processedMessages);
	}

	@Override
	public <T extends Message> Flowable<MessageFromPeer<T>> messagesOf(Class<T> messageType) {
		return Flowable.fromPublisher(peerMessageProcessor)
			.filter(p -> messageType.isInstance(p.getSecond()))
			.map(p -> new MessageFromPeer<>(p.getFirst(), messageType.cast(p.getSecond())));
	}

	@Override
	public void close() {
		this.transports.forEach(this::closeWithLog);
		this.transports.clear();
		this.outboundThreadPool.stop();
	}

	@Override
	public void sendSystemMessage(TransportInfo transportInfo, SystemMessage message) {
		PeerWithTransport peer = new PeerWithTransport(transportInfo);
		OutboundMessageEvent event = new OutboundMessageEvent(peer, message, System.nanoTime() - timeBase);
		if (!outboundQueue.offer(event) && outboundLogRateLimiter.tryAcquire()) {
			log.error("Outbound message to {} dropped", peer);
		}
	}

	@Override
	public void send(Peer peer, Message message) {
		OutboundMessageEvent event = new OutboundMessageEvent(peer, message, System.nanoTime() - timeBase);
		if (!outboundQueue.offer(event) && outboundLogRateLimiter.tryAcquire()) {
			log.error("Outbound message to {} dropped", peer);
		}
	}

	private void outboundMessageProcessor(OutboundMessageEvent outbound) {
		this.counters.set(CounterType.MESSAGES_OUTBOUND_PENDING, outboundQueue.size());
		messageDispatcher.send(connectionManager, outbound);
	}

	private void closeWithLog(Transport t) {
		try {
			t.close();
		} catch (IOException e) {
			log.error(String.format("Error closing transport %s", t), e);
		}
	}
}
