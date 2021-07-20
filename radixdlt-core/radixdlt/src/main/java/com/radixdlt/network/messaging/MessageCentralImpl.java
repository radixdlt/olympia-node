/* Copyright 2021 Radix DLT Ltd incorporated in England.
 * 
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 * 
 * radixfoundation.org/licenses/LICENSE-v1
 * 
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 * 
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 * 
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 * 
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system 
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 * 
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 * 
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 * 
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 * 
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 * 
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 * 
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 * 
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.network.messaging;

import java.util.Objects;
import java.util.Optional;

import com.google.inject.Provider;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
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
		SystemCounters counters,
		Provider<PeerControl> peerControl
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
			serialization,
			peerControl
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
