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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import io.reactivex.rxjava3.core.Observable;

import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
			this.content = Objects.requireNonNull(content);
			this.sender = sender;
			this.receiver = receiver;
			this.delay = delay;
			this.delayAfterPrevious = delayAfterPrevious;
		}

		private static MessageInTransit newMessage(Object content, BFTNode sender, BFTNode receiver) {
			return new MessageInTransit(content, sender, receiver, 0, 0);
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

	public class SimulatedNetworkImpl implements
		ProposalBroadcaster, SyncVerticesResponseSender, SyncEpochsRPCSender, BFTEventsRx,
		SyncVerticesRPCRx, SyncEpochsRPCRx {
		private final Observable<Object> myMessages;
		private final BFTNode thisNode;

		private SimulatedNetworkImpl(BFTNode node) {
			this.thisNode = node;
			// filter only relevant messages (appropriate target and if receiving is allowed)
			this.myMessages = receivedMessages
				.filter(msg -> msg.receiver.equals(node))
				.groupBy(MessageInTransit::getSender)
				.serialize()
				.flatMap(groupedObservable ->
					channelCommunication
						.transform(groupedObservable.getKey(), node, groupedObservable)
						.map(MessageInTransit::getContent)
				)
				.publish()
				.refCount();
		}

		@Override
		public void broadcastProposal(Proposal proposal, Set<BFTNode> nodes) {
			for (BFTNode reader : nodes) {
				receivedMessages.onNext(MessageInTransit.newMessage(proposal, thisNode, reader));
			}
		}

		@Override
		public void sendGetVerticesResponse(BFTNode node, ImmutableList<VerifiedVertex> vertices) {
			GetVerticesResponse vertexResponse = new GetVerticesResponse(thisNode, vertices);
			receivedMessages.onNext(MessageInTransit.newMessage(vertexResponse, thisNode, node));
		}

		@Override
		public void sendGetVerticesErrorResponse(BFTNode node, HighQC syncInfo) {
			GetVerticesErrorResponse vertexResponse = new GetVerticesErrorResponse(thisNode, syncInfo);
			receivedMessages.onNext(MessageInTransit.newMessage(vertexResponse, thisNode, node));
		}

		@Override
		public void sendGetEpochRequest(BFTNode node, long epoch) {
			GetEpochRequest getEpochRequest = new GetEpochRequest(thisNode, epoch);
			receivedMessages.onNext(MessageInTransit.newMessage(getEpochRequest, thisNode, node));
		}

		@Override
		public void sendGetEpochResponse(BFTNode node, VerifiedLedgerHeaderAndProof ancestor) {
			GetEpochResponse getEpochResponse = new GetEpochResponse(thisNode, ancestor);
			receivedMessages.onNext(MessageInTransit.newMessage(getEpochResponse, thisNode, node));
		}

		@Override
		public Observable<ConsensusEvent> bftEvents() {
			return Observable.merge(
				myMessages.ofType(ConsensusEvent.class),
				remoteEvents(Vote.class).map(RemoteEvent::getEvent)
			);
		}

		@Override
		public Observable<GetVerticesResponse> responses() {
			return myMessages.ofType(GetVerticesResponse.class);
		}

		@Override
		public Observable<GetVerticesErrorResponse> errorResponses() {
			return myMessages.ofType(GetVerticesErrorResponse.class);
		}

		@Override
		public Observable<GetEpochRequest> epochRequests() {
			return myMessages.ofType(GetEpochRequest.class);
		}

		@Override
		public Observable<GetEpochResponse> epochResponses() {
			return myMessages.ofType(GetEpochResponse.class);
		}

		public <T> Observable<RemoteEvent<T>> remoteEvents(Class<T> eventClass) {
			return myMessages.ofType(RemoteEvent.class)
				.flatMapMaybe(e -> RemoteEvent.ofEventType(e, eventClass));
		}

		public <T> RemoteEventDispatcher<T> remoteEventDispatcher(Class<T> eventClass) {
			return (node, event) -> sendRemoteEvent(node, event, eventClass);
		}

		private <T> void sendRemoteEvent(BFTNode node, T event, Class<T> eventClass) {
			RemoteEvent<T> remoteEvent = RemoteEvent.create(thisNode, event, eventClass);
			receivedMessages.onNext(MessageInTransit.newMessage(remoteEvent, thisNode, node));
		}
	}

	public SimulatedNetworkImpl getNetwork(BFTNode forNode) {
		return receivers.computeIfAbsent(forNode, SimulatedNetworkImpl::new);
	}
}
