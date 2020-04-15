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

package com.radixdlt.middleware2.network;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.EventCoordinatorNetworkSender;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vote;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Collections;
import java.util.Deque;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Simple simulated network implementation that just sends messages to itself with a configurable latency.
 */
public class TestEventCoordinatorNetwork {
	private final Random rng;
	private final int minimumLatency;
	private final int maximumLatency;
	private final boolean preserveOrder;

	private final Deque<MessageInTransit> orderedMessageBuffer;
	private final PublishSubject<MessageInTransit> receivedMessages;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private final Set<ECPublicKey> sendingDisabled = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Set<ECPublicKey> receivingDisabled = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private TestEventCoordinatorNetwork(int minimumLatency, int maximumLatency, long rngSeed, boolean preserveOrder) {
		this.preserveOrder = preserveOrder;
		if (minimumLatency < 0) {
			throw new IllegalArgumentException("minimumLatency must be >= 0 but was " + minimumLatency);
		}
		if (maximumLatency < 0) {
			throw new IllegalArgumentException("maximumLatency must be >= 0 but was " + maximumLatency);
		}
		this.minimumLatency = minimumLatency;
		this.maximumLatency = maximumLatency;
		this.rng = new Random(rngSeed);
		this.orderedMessageBuffer = new LinkedBlockingDeque<>();
		this.receivedMessages = PublishSubject.create();
	}

	/**
	 * Creates a latent simulated network with a fixed latency and all messages delivered in order.
	 * @param fixedLatency The fixed latency (may be 0)
	 * @return a network
	 */
	public static TestEventCoordinatorNetwork orderedLatent(int fixedLatency) {
		return new TestEventCoordinatorNetwork(fixedLatency, fixedLatency, 0, true);
	}

	/**
	 * Creates a latent simulated network with a randomised bounded latency and all messages delivered in order.
	 * @param minimumLatency The minimum latency (inclusive)
	 * @param maximumLatency The maximum latency (inclusive)
	 * @return a network
	 */
	public static TestEventCoordinatorNetwork orderedRandomlyLatent(int minimumLatency, int maximumLatency) {
		return orderedRandomlyLatent(minimumLatency, maximumLatency, System.currentTimeMillis());
	}

	/**
	 * Creates a latent simulated network with a randomised bounded latency and all messages delivered in order.
	 * @param minimumLatency The minimum latency (inclusive)
	 * @param maximumLatency The maximum latency (inclusive)
	 * @param rngSeed The seed to use for random operations
	 * @return a network
	 */
	public static TestEventCoordinatorNetwork orderedRandomlyLatent(int minimumLatency, int maximumLatency, long rngSeed) {
		return new TestEventCoordinatorNetwork(minimumLatency, maximumLatency, rngSeed, true);
	}

	public void setSendingDisable(ECPublicKey validatorId, boolean disable) {
		if (disable) {
			sendingDisabled.add(validatorId);
		} else {
			sendingDisabled.remove(validatorId);
		}
	}

	public void setReceivingDisable(ECPublicKey validatorId, boolean disable) {
		if (disable) {
			receivingDisabled.add(validatorId);
		} else {
			receivingDisabled.remove(validatorId);
		}
	}

	private int getRandomLatency() {
		if (minimumLatency == maximumLatency) {
			return minimumLatency;
		} else {
			return minimumLatency + rng.nextInt(maximumLatency - minimumLatency + 1);
		}
	}

	public EventCoordinatorNetworkSender getNetworkSender(ECPublicKey forNode) {
		Consumer<MessageInTransit> sendMessageSink = message -> {
			if (!sendingDisabled.contains(forNode)) {
				if (preserveOrder) {
					orderedMessageBuffer.push(message);
				}
				executorService.schedule(() -> {
					if (preserveOrder) {
						MessageInTransit otherMessage = orderedMessageBuffer.pollLast();
						if (otherMessage == null) {
							throw new IllegalStateException("No message available in message buffer");
						}
						receivedMessages.onNext(otherMessage);
					} else {
						receivedMessages.onNext(message);
					}
				}, getRandomLatency(), TimeUnit.MILLISECONDS);
			}
		};
		return new EventCoordinatorNetworkSender() {
			@Override
			public void broadcastProposal(Proposal proposal) {
				sendMessageSink.accept(MessageInTransit.broadcast(proposal));
			}

			@Override
			public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
				sendMessageSink.accept(MessageInTransit.send(newView, newViewLeader));
			}

			@Override
			public void sendVote(Vote vote, ECPublicKey leader) {
				sendMessageSink.accept(MessageInTransit.send(vote, leader));
			}
		};
	}

	public EventCoordinatorNetworkRx getNetworkRx(ECPublicKey forNode) {
		// filter only relevant messages (appropriate target and if receiving is allowed)
		Observable<Object> myMessages = receivedMessages
			.filter(message -> !receivingDisabled.contains(forNode))
			.filter(message -> message.isRelevantFor(forNode))
			.map(MessageInTransit::getContent);
		return () -> myMessages.ofType(ConsensusEvent.class);
	}

	public int getMaximumLatency() {
		return maximumLatency;
	}

	private static final class MessageInTransit {
		private final Object content;
		private final ECPublicKey target; // may be null if broadcast

		private MessageInTransit(Object content, ECPublicKey target) {
			this.content = Objects.requireNonNull(content);
			this.target = target;
		}

		private static MessageInTransit broadcast(Object content) {
			return new MessageInTransit(content, null);
		}

		private static MessageInTransit send(Object content, ECPublicKey receiver) {
			return new MessageInTransit(content, receiver);
		}

		private Object getContent() {
			return this.content;
		}

		private boolean isRelevantFor(ECPublicKey node) {
			return target == null || node.equals(target);
		}
	}
}
