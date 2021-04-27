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

import java.util.Objects;
import java.util.Optional;

import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerManager;
import io.reactivex.rxjava3.core.Observable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;
import org.radix.utils.SimpleThreadPool;

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Inject;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.serialization.Serialization;

final class MessageCentralImpl implements MessageCentral {
	private static final Logger log = LogManager.getLogger();

	// Dependencies
	private final SystemCounters counters;

	// Message dispatching
	private final MessageDispatcher messageDispatcher;
	private final MessagePreprocessor messagePreprocessor;

	// Our time base for System.nanoTime() differences.  Per documentation can only compare deltas
	private final long timeBase = System.nanoTime();

	private final RateLimiter outboundLogRateLimiter = RateLimiter.create(1.0);
	private final RateLimiter discardedInboundMessagesLogRateLimiter = RateLimiter.create(1.0);

	private final Observable<MessageFromPeer<Message>> peerMessages;

	// Outbound message handling
	private final SimpleBlockingQueue<OutboundMessageEvent> outboundQueue;
	private final SimpleThreadPool<OutboundMessageEvent> outboundThreadPool;

	@Inject
	MessageCentralImpl(
		MessageCentralConfiguration config,
		Serialization serialization,
		PeerManager peerManager,
		TimeSupplier timeSource,
		EventQueueFactory<OutboundMessageEvent> outboundEventQueueFactory,
		SystemCounters counters
	) {
		this.counters = Objects.requireNonNull(counters);
		this.outboundQueue = outboundEventQueueFactory.createEventQueue(
			config.messagingOutboundQueueMax(16384),
			OutboundMessageEvent.comparator()
		);

		Objects.requireNonNull(timeSource);
		Objects.requireNonNull(serialization);

		this.messageDispatcher = new MessageDispatcher(
			counters,
			config,
			serialization,
			timeSource,
			peerManager
		);

		this.messagePreprocessor = new MessagePreprocessor(
			counters,
			config,
			timeSource,
			serialization
		);

		// Start outbound processing thread
		this.outboundThreadPool = new SimpleThreadPool<>(
			"Outbound message processing",
			1, // Ensure messages sent in-order
			outboundQueue::take,
			this::outboundMessageProcessor,
			log
		);
		this.outboundThreadPool.start();

		this.peerMessages = peerManager.messages()
			.map(this::processInboundMessage)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.publish()
			.autoConnect();
	}

	private Optional<MessageFromPeer<Message>> processInboundMessage(InboundMessage inboundMessage) {
		try {
			return this.messagePreprocessor.process(inboundMessage).fold(
				error -> {
					final var logLevel =
						discardedInboundMessagesLogRateLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
					log.log(logLevel, "Dropping inbound message from {} because of {}", inboundMessage.source(), error);
					return Optional.empty();
				},
				messageFromPeer -> {
					if (log.isTraceEnabled()) {
						log.trace("Received from {}: {}", messageFromPeer.getSource(), messageFromPeer.getMessage());
					}
					return Optional.of(messageFromPeer);
				}
			);
		} catch (Exception ex) {
			final var msg = String.format("Message preprocessing from %s failed", inboundMessage.source());
			log.error(msg, ex);
			return Optional.empty();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Message> Observable<MessageFromPeer<T>> messagesOf(Class<T> messageType) {
		return this.peerMessages
			.filter(p -> messageType.isInstance(p.getMessage()))
			.map(p -> (MessageFromPeer<T>) p);
	}

	@Override
	public void close() {
		this.outboundThreadPool.stop();
	}

	@Override
	public void send(NodeId receiver, Message message) {
		final var event = new OutboundMessageEvent(receiver, message, System.nanoTime() - timeBase);
		if (!outboundQueue.offer(event) && outboundLogRateLimiter.tryAcquire()) {
			log.error("Outbound message to {} dropped", receiver);
		}
	}

	private void outboundMessageProcessor(OutboundMessageEvent outbound) {
		this.counters.set(CounterType.MESSAGES_OUTBOUND_PENDING, outboundQueue.size());
		messageDispatcher.send(outbound);
	}
}
