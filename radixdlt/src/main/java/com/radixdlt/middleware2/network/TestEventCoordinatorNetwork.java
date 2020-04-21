/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import com.google.common.collect.Sets;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.EventCoordinatorNetworkSender;
import com.radixdlt.consensus.GetVertexRequest;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.core.Observable;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Simple simulated network implementation that just sends messages to itself with a configurable latency.
 */
public class TestEventCoordinatorNetwork {
	public static final int DEFAULT_LATENCY = 50;

	public interface LatencyProvider {
		Integer nextLatency(ECPublicKey from, ECPublicKey to);
	}

	private final Subject<MessageInTransit> receivedMessages;
	private final Map<ECPublicKey, EventCoordinatorNetworkRx> readers = new ConcurrentHashMap<>();
	private final Set<ECPublicKey> sendingDisabled = Sets.newConcurrentHashSet();
	private final Set<ECPublicKey> receivingDisabled = Sets.newConcurrentHashSet();
	private final LatencyProvider latencyProvider;

	private TestEventCoordinatorNetwork(LatencyProvider latencyProvider) {
		this.latencyProvider = latencyProvider;
		this.receivedMessages = ReplaySubject.<MessageInTransit>create(5) // To catch startup timing issues
			.toSerialized();
	}

	public static class Builder {
		private LatencyProvider latencyProvider = (from, to) -> DEFAULT_LATENCY;

		private Builder() {
		}

		public Builder latencyProvider(LatencyProvider latencyProvider) {
			this.latencyProvider = latencyProvider;
			return this;
		}

		public TestEventCoordinatorNetwork build() {
			return new TestEventCoordinatorNetwork(latencyProvider);
		}
	}

	public static Builder builder() {
		return new Builder();
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

	public EventCoordinatorNetworkSender getNetworkSender(ECPublicKey forNode) {
		return new EventCoordinatorNetworkSender() {
			@Override
			public void broadcastProposal(Proposal proposal) {
				for (ECPublicKey reader : readers.keySet()) {
					receivedMessages.onNext(MessageInTransit.send(proposal, forNode, reader));
				}
			}

			@Override
			public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
				receivedMessages.onNext(MessageInTransit.send(newView, forNode, newViewLeader));
			}

			@Override
			public void sendVote(Vote vote, ECPublicKey leader) {
				receivedMessages.onNext(MessageInTransit.send(vote, forNode, leader));
			}

			@Override
			public Single<Vertex> getVertex(Hash vertexId, ECPublicKey node) {
				final GetVertexRequest request = new GetVertexRequest(vertexId, forNode);
				return receivedMessages.map(MessageInTransit::getContent)
					.ofType(Vertex.class)
					.filter(v -> v.getId().equals(vertexId))
					.firstOrError()
					.doOnSubscribe(d -> receivedMessages.onNext(MessageInTransit.send(request, forNode, node)));
			}

			@Override
			public void sendGetVertexResponse(Vertex vertex, ECPublicKey node) {
				receivedMessages.onNext(MessageInTransit.send(vertex, forNode, node));
			}
		};
	}

	public EventCoordinatorNetworkRx getNetworkRx(ECPublicKey forNode) {
		// filter only relevant messages (appropriate target and if receiving is allowed)
		return readers.computeIfAbsent(forNode, node -> new EventCoordinatorNetworkRx() {
			final Observable<Object> myMessages = receivedMessages
					.filter(msg -> !sendingDisabled.contains(msg.sender))
					.filter(msg -> !receivingDisabled.contains(msg.target))
					.filter(msg -> msg.target.equals(node))
					.map(msg -> {
						if (msg.sender.equals(node)) {
							return msg;
						} else {
							return msg.delayed(latencyProvider.nextLatency(msg.sender, msg.target));
						}
					})
					.timestamp(TimeUnit.MILLISECONDS)
					.scan((msg1, msg2) -> {
						int delayCarryover = (int) Math.max(msg1.time() + msg1.value().delay - msg2.time(), 0);
						int additionalDelay = (int) (msg2.value().delay - delayCarryover);
						if (additionalDelay > 0) {
							return new Timed<>(msg2.value().delayed(additionalDelay), msg2.time(), msg2.unit());
						} else {
							return msg2;
						}
					})
					.delay(p -> Observable.timer(p.value().delay, TimeUnit.MILLISECONDS))
					.map(Timed::value)
					.map(MessageInTransit::getContent);

			@Override
			public Observable<ConsensusEvent> consensusEvents() {
				return myMessages.ofType(ConsensusEvent.class);
			}

			@Override
			public Observable<GetVertexRequest> rpcRequests() {
				return myMessages.ofType(GetVertexRequest.class);
			}
		});
	}

	private static final class MessageInTransit {
		private final Object content;
		private final ECPublicKey sender;
		private final ECPublicKey target;
		private final long delay;

		private MessageInTransit(Object content, ECPublicKey sender, ECPublicKey target, long delay) {
			this.content = Objects.requireNonNull(content);
			this.sender = sender;
			this.target = target;
			this.delay = delay;
		}

		private static MessageInTransit send(Object content, ECPublicKey sender, ECPublicKey receiver) {
			return new MessageInTransit(content, sender, receiver, 0);
		}

		private MessageInTransit delayed(long delay) {
			return new MessageInTransit(content, sender, target, delay);
		}

		private Object getContent() {
			return this.content;
		}
	}
}
