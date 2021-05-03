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

package com.radixdlt.integration.distributed.simulation.network;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.environment.rx.RxRemoteEnvironment;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;

import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple simulated network implementation that just sends messages to itself with a configurable latency.
 */
public class SimulationNetwork {
	public static final int DEFAULT_LATENCY = 50;

	public static final class MessageInTransit {
		private final Object content;
		private final BFTNode sender;
		private final BFTNode receiver;
		private final long delay;
		private final long delayAfterPrevious;

		private MessageInTransit(Object content, BFTNode sender, BFTNode receiver, long delay, long delayAfterPrevious) {
			if (content instanceof RemoteEvent) {
				throw new IllegalArgumentException("Message in transit should not be RemoteEvent");
			}

			this.content = Objects.requireNonNull(content);
			this.sender = sender;
			this.receiver = receiver;
			this.delay = delay;
			this.delayAfterPrevious = delayAfterPrevious;
		}

		private static MessageInTransit newMessage(Object content, BFTNode sender, BFTNode receiver) {
			return new MessageInTransit(content, sender, receiver, 0, 0);
		}

		public <T> Maybe<T> localEvent(Class<T> eventClass) {
			if (sender.equals(receiver) && eventClass.isInstance(content)) {
				return Maybe.just(eventClass.cast(content));
			}

			return Maybe.empty();
		}

		public <T> Maybe<RemoteEvent<T>> remoteEvent(Class<T> eventClass) {
			if (!sender.equals(receiver) && eventClass.isInstance(content)) {
				return Maybe.just(RemoteEvent.create(sender, eventClass.cast(content)));
			}

			return Maybe.empty();
		}

		MessageInTransit delayed(long delay) {
			return new MessageInTransit(content, sender, receiver, delay, delay);
		}

		MessageInTransit delayAfterPrevious(long delayAfterPrevious) {
			return new MessageInTransit(content, sender, receiver, delay, delayAfterPrevious);
		}

		public long getDelayAfterPrevious() {
			return delayAfterPrevious;
		}

		public long getDelay() {
			return delay;
		}

		public Object getContent() {
			return this.content;
		}

		public BFTNode getSender() {
			return sender;
		}

		public BFTNode getReceiver() {
			return receiver;
		}

		@Override
		public String toString() {
			return String.format("%s %s -> %s %d %d",
				content,
				sender.getSimpleName(),
				receiver.getSimpleName(),
				delay,
				delayAfterPrevious
			);
		}
	}

	public interface ChannelCommunication {
		Observable<MessageInTransit> transform(BFTNode sender, BFTNode receiver, Observable<MessageInTransit> messages);
	}

	private final Subject<MessageInTransit> receivedMessages;
	private final Map<BFTNode, SimulatedNetworkImpl> receivers = new ConcurrentHashMap<>();
	private final ChannelCommunication channelCommunication;

	@Inject
	public SimulationNetwork(ChannelCommunication channelCommunication) {
		this.channelCommunication = Objects.requireNonNull(channelCommunication);
		this.receivedMessages = ReplaySubject.<MessageInTransit>createWithSize(128) // To catch startup timing issues
			.toSerialized();
	}

	public class SimulatedNetworkImpl implements RxRemoteEnvironment {
		private final Flowable<MessageInTransit> myMessages;
		private final BFTNode thisNode;

		private SimulatedNetworkImpl(BFTNode node) {
			this.thisNode = node;
			// filter only relevant messages (appropriate target and if receiving is allowed)
			this.myMessages = Flowable.fromObservable(receivedMessages
				.filter(msg -> msg.receiver.equals(node))
				.groupBy(MessageInTransit::getSender)
				.serialize()
				.flatMap(groupedObservable ->
					channelCommunication
						.transform(groupedObservable.getKey(), node, groupedObservable)
				)
				.publish()
				.refCount(), BackpressureStrategy.BUFFER)
			.onBackpressureBuffer(255, false, true /* unbounded */);
		}

		public <T> Flowable<T> localEvents(Class<T> eventClass) {
			return myMessages.flatMapMaybe(m -> m.localEvent(eventClass));
		}

		@Override
		public <T> Flowable<RemoteEvent<T>> remoteEvents(Class<T> eventClass) {
			return myMessages.flatMapMaybe(m -> m.remoteEvent(eventClass));
		}

		public <T> RemoteEventDispatcher<T> remoteEventDispatcher(Class<T> eventClass) {
			return this::sendRemoteEvent;
		}

		private <T> void sendRemoteEvent(BFTNode node, T event) {
			receivedMessages.onNext(MessageInTransit.newMessage(event, thisNode, node));
		}
	}

	public SimulatedNetworkImpl getNetwork(BFTNode forNode) {
		return receivers.computeIfAbsent(forNode, SimulatedNetworkImpl::new);
	}
}
