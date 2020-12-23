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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.radixdlt.crypto.Hasher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.SystemMessage;
import org.radix.utils.SimpleThreadPool;
import org.xerial.snappy.Snappy;

import com.google.common.annotations.VisibleForTesting;
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

	private static final MessageListenerList EMPTY_MESSAGE_LISTENER_LIST = new MessageListenerList();

	// Dependencies
	private final Serialization serialization;
	private final TransportManager connectionManager;
	private final AddressBook addressBook;
	private final SystemCounters counters;

	// Local data

	// Message dispatching
	private final MessageDispatcher messageDispatcher;

	// Listeners
	private final ConcurrentHashMap<Class<? extends Message>, MessageListenerList> listeners = new ConcurrentHashMap<>();

	// Our time base for System.nanoTime() differences.  Per documentation can only compare deltas
	private final long timeBase = System.nanoTime();

	// Listeners we are managing
	private final List<Transport> transports;

	// Limit rate at which dropped packet logs are produced
	private final RateLimiter inboundLogRateLimiter = RateLimiter.create(1.0);
	private final RateLimiter outboundLogRateLimiter = RateLimiter.create(1.0);

	// Inbound message handling
	private final SimpleBlockingQueue<InboundMessageEvent> inboundQueue;
	private final SimpleThreadPool<InboundMessageEvent> inboundThreadPool;

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
		EventQueueFactory<InboundMessageEvent> inboundEventQueueFactory,
		EventQueueFactory<OutboundMessageEvent> outboundEventQueueFactory,
		LocalSystem localSystem,
		SystemCounters counters,
		Hasher hasher,
		HashSigner hashSigner
	) {
		this.counters = Objects.requireNonNull(counters);
		this.inboundQueue = inboundEventQueueFactory.createEventQueue(config.messagingInboundQueueMax(8192), InboundMessageEvent.comparator());
		this.outboundQueue = outboundEventQueueFactory.createEventQueue(config.messagingOutboundQueueMax(16384), OutboundMessageEvent.comparator());

		this.serialization = Objects.requireNonNull(serialization);
		this.connectionManager = Objects.requireNonNull(transportManager);
		this.addressBook = Objects.requireNonNull(addressBook);

		Objects.requireNonNull(timeSource);
		this.messageDispatcher = new MessageDispatcher(
			counters,
			config,
			serialization,
			timeSource,
			localSystem,
			this.addressBook,
			hasher,
			hashSigner
		);

		this.transports = Lists.newArrayList(transportManager.transports());

		// Start inbound processing thread
		this.inboundThreadPool = new SimpleThreadPool<>(
			"Inbound message processing",
			1, // Ensure messages processed in-order
			inboundQueue::take,
			this::inboundMessageProcessor,
			log
		);
		this.inboundThreadPool.start();

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
		this.transports.forEach(tl -> tl.start(this::inboundMessage));
	}

	@Override
	public void close() {
		this.transports.forEach(this::closeWithLog);
		this.transports.clear();

		inboundThreadPool.stop();
		outboundThreadPool.stop();
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

	@Override
	public void inject(TransportInfo source, Message message) {
		InboundMessageEvent event = new InboundMessageEvent(source, message, System.nanoTime() - timeBase);
		if (!inboundQueue.offer(event) && inboundLogRateLimiter.tryAcquire()) {
			log.error("Injected message from {} dropped", source);
		}
	}

	@Override
	public <T extends Message> void addListener(Class<T> messageType, MessageListener<T> listener) {
		Objects.requireNonNull(messageType);
		this.listeners.computeIfAbsent(messageType, k -> new MessageListenerList()).addMessageListener(listener);
	}

	@Override
	public <T extends Message> void removeListener(Class<T> messageType, MessageListener<T> listener) {
		Objects.requireNonNull(messageType);
		this.listeners.getOrDefault(messageType, EMPTY_MESSAGE_LISTENER_LIST).removeMessageListener(listener);
	}

	@Override
	public <T extends Message> void removeListener(MessageListener<T> listener) {
		this.listeners.values().forEach(mll -> mll.removeMessageListener(listener));
	}

	@VisibleForTesting
	int listenersSize() {
		return listeners.values().stream().mapToInt(MessageListenerList::size).sum();
	}

	private void inboundMessage(InboundMessage inboundMessage) {
		byte[] messageBytes = inboundMessage.message();
		this.counters.add(CounterType.NETWORKING_RECEIVED_BYTES, messageBytes.length);
		Message message = deserialize(messageBytes);
		inject(inboundMessage.source(), message);
	}

	private void inboundMessageProcessor(InboundMessageEvent inbound) {
		this.counters.set(CounterType.MESSAGES_INBOUND_PENDING, inboundQueue.size());
		MessageListenerList ls = this.listeners.getOrDefault(inbound.message().getClass(), EMPTY_MESSAGE_LISTENER_LIST);
		messageDispatcher.receive(ls, inbound);
	}

	private void outboundMessageProcessor(OutboundMessageEvent outbound) {
		this.counters.set(CounterType.MESSAGES_OUTBOUND_PENDING, outboundQueue.size());
		messageDispatcher.send(connectionManager, outbound);
	}

	private Message deserialize(byte[] in) {
		try {
			byte[] uncompressed = Snappy.uncompress(in);

			return serialization.fromDson(uncompressed, Message.class);

		} catch (IOException e) {
			throw new UncheckedIOException("While deserializing message", e);
		}
	}

	private void closeWithLog(Transport t) {
		try {
			t.close();
		} catch (IOException e) {
			log.error(String.format("Error closing transport %s", t), e);
		}
	}
}
