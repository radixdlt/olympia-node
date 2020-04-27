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

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.EventCoordinatorNetworkSender;
import com.radixdlt.consensus.GetVertexRequest;
import com.radixdlt.consensus.GetVertexResponse;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.core.Observable;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Simple simulated network implementation that just sends messages to itself with a configurable latency.
 */
public class TestEventCoordinatorNetwork {
	public static final int DEFAULT_LATENCY = 50;

	public static final class MessageInTransit {
		private final Object content;
		private final ECPublicKey sender;
		private final ECPublicKey receiver;
		private final long delay;

		private MessageInTransit(Object content, ECPublicKey sender, ECPublicKey receiver, long delay) {
			this.content = Objects.requireNonNull(content);
			this.sender = sender;
			this.receiver = receiver;
			this.delay = delay;
		}

		private static MessageInTransit newMessage(Object content, ECPublicKey sender, ECPublicKey receiver) {
			return new MessageInTransit(content, sender, receiver, 0);
		}

		private MessageInTransit delayed(long delay) {
			return new MessageInTransit(content, sender, receiver, delay);
		}

		public Object getContent() {
			return this.content;
		}

		public ECPublicKey getSender() {
			return sender;
		}

		public ECPublicKey getReceiver() {
			return receiver;
		}

		@Override
		public String toString() {
			return String.format("%s %s -> %s %d",
				content,
				sender.euid().toString().substring(0, 6),
				receiver.euid().toString().substring(0, 6),
				delay
			);
		}
	}

	/**
	 * The latency configuration for a network
	 */
	public interface LatencyProvider {

		/**
		 * If >= 0, returns the latency in milliseconds of the next message.
		 * If < 0, signifies to drop the next message.
		 *
		 * @param msg the next message
		 * @return the latency in milliseconds if >= 0, otherwise a negative number signifies a drop
		 */
		int nextLatency(MessageInTransit msg);
	}

	private final Subject<MessageInTransit> receivedMessages;
	private final Map<ECPublicKey, SimulatedReceiver> receivers = new ConcurrentHashMap<>();
	private final LatencyProvider latencyProvider;

	private TestEventCoordinatorNetwork(LatencyProvider latencyProvider) {
		this.latencyProvider = latencyProvider;
		this.receivedMessages = ReplaySubject.<MessageInTransit>create(5) // To catch startup timing issues
			.toSerialized();
	}

	public static class Builder {
		private LatencyProvider latencyProvider = msg -> DEFAULT_LATENCY;

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

	public EventCoordinatorNetworkSender getNetworkSender(ECPublicKey forNode) {
		return new EventCoordinatorNetworkSender() {
			@Override
			public void broadcastProposal(Proposal proposal) {
				for (ECPublicKey reader : receivers.keySet()) {
					receivedMessages.onNext(MessageInTransit.newMessage(proposal, forNode, reader));
				}
			}

			@Override
			public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
				receivedMessages.onNext(MessageInTransit.newMessage(newView, forNode, newViewLeader));
			}

			@Override
			public void sendVote(Vote vote, ECPublicKey leader) {
				receivedMessages.onNext(MessageInTransit.newMessage(vote, forNode, leader));
			}

			@Override
			public Single<Vertex> getVertex(Hash vertexId, ECPublicKey node) {
				return Single.create(emitter -> {
					Disposable d = receivers.computeIfAbsent(forNode, SimulatedReceiver::new).myMessages
						.ofType(GetVertexResponse.class)
						.filter(v -> v.getVertexId().equals(vertexId))
						.firstOrError()
						.map(GetVertexResponse::getVertex)
						.subscribe(emitter::onSuccess);
					emitter.setDisposable(d);

					final GetVertexRequest request = new GetVertexRequest(
						vertexId,
						vertex -> {
							GetVertexResponse vertexResponse = new GetVertexResponse(vertexId, vertex);
							receivedMessages.onNext(MessageInTransit.newMessage(vertexResponse, node, forNode));
						}
					);
					receivedMessages.onNext(MessageInTransit.newMessage(request, forNode, node));
				});
			}
		};
	}

	private class SimulatedReceiver implements EventCoordinatorNetworkRx {
		private final Observable<Object> myMessages;

		private SimulatedReceiver(ECPublicKey node) {
			// filter only relevant messages (appropriate target and if receiving is allowed)
			this.myMessages = receivedMessages
				.filter(msg -> msg.receiver.equals(node))
				.map(msg -> {
					if (msg.sender.equals(node)) {
						return msg;
					} else {
						return msg.delayed(latencyProvider.nextLatency(msg));
					}
				})
				.filter(msg -> msg.delay >= 0)
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
				.delay(p -> {
					if (p.value().delay > 0) {
						return Observable.timer(p.value().delay, TimeUnit.MILLISECONDS, Schedulers.io());
					} else {
						return Observable.just(0L);
					}
				})
				.map(Timed::value)
				.map(MessageInTransit::getContent)
				.publish()
				.refCount();
		}

		@Override
		public Observable<ConsensusEvent> consensusEvents() {
			return myMessages.ofType(ConsensusEvent.class);
		}

		@Override
		public Observable<GetVertexRequest> rpcRequests() {
			return myMessages.ofType(GetVertexRequest.class);
		}
	}

	public EventCoordinatorNetworkRx getNetworkRx(ECPublicKey forNode) {
		return receivers.computeIfAbsent(forNode, SimulatedReceiver::new);
	}
}
