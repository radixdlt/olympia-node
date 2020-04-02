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

package com.radixdlt.network;

import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.EventCoordinatorNetworkSender;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.identifiers.EUID;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Collections;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Simple simulated network implementation that just sends messages to itself with a configurable delay.
 */
public class TestEventCoordinatorNetwork {
	private final Random rng;
	private final int minimumDelay;
	private final int maximumDelay;

	private final PublishSubject<MessageInTransit> messages;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private final Set<EUID> sendingDisabled = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Set<EUID> receivingDisabled = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private TestEventCoordinatorNetwork(int minimumDelay, int maximumDelay, long rngSeed) {
		if (minimumDelay < 0) {
			throw new IllegalArgumentException("minimumDelay must be >= 0 but was " + minimumDelay);
		}
		if (maximumDelay < 0) {
			throw new IllegalArgumentException("maximumDelay must be >= 0 but was " + maximumDelay);
		}
		this.minimumDelay = minimumDelay;
		this.maximumDelay = maximumDelay;
		this.rng = new Random(rngSeed);
		this.messages = PublishSubject.create();
	}

	/**
	 * Creates a perfect simulated network with a fixed delay.
	 * @param fixedDelay The fixed delay (may be 0)
	 * @return a network
	 */
	public static TestEventCoordinatorNetwork perfect(int fixedDelay) {
		return new TestEventCoordinatorNetwork(fixedDelay, fixedDelay, 0);
	}

	/**
	 * Creates an unreliable simulated network with a randomised bounded delay.
	 * @param minimumDelay The minimum delay (inclusive)
	 * @param maximumDelay The maximum delay (inclusive)
	 * @return a network
	 */
	public static TestEventCoordinatorNetwork unreliable(int minimumDelay, int maximumDelay) {
		return unreliable(minimumDelay, maximumDelay, System.currentTimeMillis());
	}

	/**
	 * Creates an unreliable simulated network with a randomised bounded delay.
	 * @param minimumDelay The minimum delay (inclusive)
	 * @param maximumDelay The maximum delay (inclusive)
	 * @param rngSeed The seed to use for random operations
	 * @return a network
	 */
	public static TestEventCoordinatorNetwork unreliable(int minimumDelay, int maximumDelay, long rngSeed) {
		return new TestEventCoordinatorNetwork(minimumDelay, maximumDelay, rngSeed);
	}

	public void setSendingDisable(EUID euid, boolean disable) {
		if (disable) {
			sendingDisabled.add(euid);
		} else {
			sendingDisabled.remove(euid);
		}
	}

	public void setReceivingDisable(EUID euid, boolean disable) {
		if (disable) {
			receivingDisabled.add(euid);
		} else {
			receivingDisabled.remove(euid);
		}
	}

	private int getRandomDelay() {
		if (minimumDelay == maximumDelay) {
			return minimumDelay;
		} else {
			return minimumDelay + rng.nextInt(maximumDelay - minimumDelay + 1);
		}
	}

	public EventCoordinatorNetworkSender getNetworkSender(EUID forNode) {
		Consumer<MessageInTransit> sendMessageSink = message -> {
			if (!sendingDisabled.contains(forNode)) {
				executorService.schedule(() -> messages.onNext(message), getRandomDelay(), TimeUnit.MILLISECONDS);
			}
		};
		return new EventCoordinatorNetworkSender() {
			@Override
			public void broadcastProposal(Vertex vertex) {
				sendMessageSink.accept(MessageInTransit.broadcast(vertex));
			}

			@Override
			public void sendNewView(NewView newView, EUID newViewLeader) {
				sendMessageSink.accept(MessageInTransit.send(newView, newViewLeader));
			}

			@Override
			public void sendVote(Vote vote, EUID leader) {
				sendMessageSink.accept(MessageInTransit.send(vote, leader));
			}
		};
	}

	public EventCoordinatorNetworkRx getNetworkRx(EUID forNode) {
		// filter only relevant messages (appropriate target and if receiving is allowed)
		Observable<Object> myMessages = messages
			.filter(message -> !receivingDisabled.contains(forNode))
			.filter(message -> message.isRelevantFor(forNode))
			.map(MessageInTransit::getContent);
		return new EventCoordinatorNetworkRx() {
			@Override
			public Observable<Vertex> proposalMessages() {
				return myMessages.ofType(Vertex.class);
			}

			@Override
			public Observable<NewView> newViewMessages() {
				return myMessages.ofType(NewView.class);
			}

			@Override
			public Observable<Vote> voteMessages() {
				return myMessages.ofType(Vote.class);
			}
		};
	}

	private static final class MessageInTransit {
		private final Object content;
		private final EUID target; // may be null if broadcast

		private MessageInTransit(Object content, EUID target) {
			this.content = Objects.requireNonNull(content);
			this.target = target;
		}

		private static MessageInTransit broadcast(Object content) {
			return new MessageInTransit(content, null);
		}

		private static MessageInTransit send(Object content, EUID receiver) {
			return new MessageInTransit(content, receiver);
		}

		private Object getContent() {
			return this.content;
		}

		private boolean isRelevantFor(EUID node) {
			return target == null || node.equals(target);
		}
	}
}
