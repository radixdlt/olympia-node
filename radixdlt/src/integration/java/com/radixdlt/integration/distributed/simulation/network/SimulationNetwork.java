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
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.liveness.ProceedToViewSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.RemoteSyncResponse;
import com.radixdlt.sync.StateSyncNetwork;
import com.radixdlt.sync.RemoteSyncRequest;
import com.radixdlt.ledger.DtoCommandsAndProof;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple simulated network implementation that just sends messages to itself with a configurable latency.
 */
public class SimulationNetwork {
	private static Logger log = LogManager.getLogger();

	public static final int DEFAULT_LATENCY = 50;
	private static final long LOG_WARN_DELAY = DEFAULT_LATENCY / 2;

	public static final class MessageInTransit {
		private final Object content;
		private final BFTNode sender;
		private final BFTNode receiver;
		private final long delay;
		private final long timeCreated;

		private MessageInTransit(Object content, BFTNode sender, BFTNode receiver, long delay, long delayAfterPrevious, long timeCreated) {
			this.content = Objects.requireNonNull(content);
			this.sender = sender;
			this.receiver = receiver;
			this.delay = delay;
			this.timeCreated = timeCreated;
		}

		private static MessageInTransit newMessage(Object content, BFTNode sender, BFTNode receiver) {
			return new MessageInTransit(content, sender, receiver, 0, 0, System.currentTimeMillis());
		}

		private MessageInTransit delayed(long delay) {
			return new MessageInTransit(content, sender, receiver, delay, delay, timeCreated);
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
			return String.format("%s -> %s %d %s", sender, receiver, delay, content);
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
	private final Map<BFTNode, SimulatedNetworkImpl> receivers = new ConcurrentHashMap<>();
	private final LatencyProvider latencyProvider;

	private SimulationNetwork(LatencyProvider latencyProvider) {
		this.latencyProvider = latencyProvider;
		this.receivedMessages = ReplaySubject.<MessageInTransit>create(20) // To catch startup timing issues
			.toSerialized();
	}

	public static class Builder {
		private LatencyProvider latencyProvider = msg -> DEFAULT_LATENCY;

		private Builder() {
			// Nothing
		}

		public Builder latencyProvider(LatencyProvider latencyProvider) {
			this.latencyProvider = latencyProvider;
			return this;
		}

		public SimulationNetwork build() {
			return new SimulationNetwork(latencyProvider);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public class SimulatedNetworkImpl implements
		ProposalBroadcaster, ProceedToViewSender, SyncVerticesRequestSender, SyncVerticesResponseSender, SyncEpochsRPCSender, BFTEventsRx,
		SyncVerticesRPCRx, SyncEpochsRPCRx, StateSyncNetwork {
		private final Observable<Object> myMessages;
		private final BFTNode thisNode;

		private SimulatedNetworkImpl(BFTNode node) {
			this.thisNode = node;
			// filter only relevant messages (appropriate target and if receiving is allowed)
			this.myMessages = receivedMessages
				.filter(msg -> msg.receiver.equals(node))
				.map(msg -> msg.sender.equals(node) ? msg : msg.delayed(latencyProvider.nextLatency(msg)))
				.filter(msg -> msg.delay >= 0)
				.flatMap(this::delayed)
				.doOnNext(this::checkArrivalAndLogMessage)
				.map(MessageInTransit::getContent)
				.publish()
				.refCount();
		}

		@Override
		public void broadcastProposal(Proposal proposal, Set<BFTNode> nodes) {
			for (BFTNode reader : nodes) {
				logAndSend(MessageInTransit.newMessage(proposal, thisNode, reader));
			}
		}

		@Override
		public void broadcastViewTimeout(ViewTimeout viewTimeout, Set<BFTNode> nodes) {
			for (BFTNode reader : nodes) {
				logAndSend(MessageInTransit.newMessage(viewTimeout, thisNode, reader));
			}
		}

		@Override
		public void sendVote(Vote vote, BFTNode leader) {
			logAndSend(MessageInTransit.newMessage(vote, thisNode, leader));
		}

		@Override
		public void sendGetVerticesRequest(BFTNode node, Hash id, int count) {
			final GetVerticesRequest request = new GetVerticesRequest(thisNode, id, count);
			logAndSend(MessageInTransit.newMessage(request, thisNode, node));
		}

		@Override
		public void sendGetVerticesResponse(BFTNode node, ImmutableList<VerifiedVertex> vertices) {
			GetVerticesResponse vertexResponse = new GetVerticesResponse(thisNode, vertices);
			logAndSend(MessageInTransit.newMessage(vertexResponse, thisNode, node));
		}

		@Override
		public void sendGetVerticesErrorResponse(BFTNode node, HighQC highQC) {
			GetVerticesErrorResponse vertexResponse = new GetVerticesErrorResponse(thisNode, highQC);
			logAndSend(MessageInTransit.newMessage(vertexResponse, thisNode, node));
		}

		@Override
		public void sendGetEpochRequest(BFTNode node, long epoch) {
			GetEpochRequest getEpochRequest = new GetEpochRequest(thisNode, epoch);
			logAndSend(MessageInTransit.newMessage(getEpochRequest, thisNode, node));
		}

		@Override
		public void sendGetEpochResponse(BFTNode node, VerifiedLedgerHeaderAndProof ancestor) {
			GetEpochResponse getEpochResponse = new GetEpochResponse(thisNode, ancestor);
			logAndSend(MessageInTransit.newMessage(getEpochResponse, thisNode, node));
		}

		@Override
		public Observable<ConsensusEvent> bftEvents() {
			return myMessages.ofType(ConsensusEvent.class);
		}

		@Override
		public Observable<GetVerticesRequest> requests() {
			return myMessages.ofType(GetVerticesRequest.class);
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

		@Override
		public Observable<RemoteSyncResponse> syncResponses() {
			return myMessages.ofType(RemoteSyncResponse.class);
		}

		@Override
		public Observable<RemoteSyncRequest> syncRequests() {
			return myMessages.ofType(RemoteSyncRequest.class);
		}

		@Override
		public void sendSyncRequest(BFTNode node, DtoLedgerHeaderAndProof currentHeader) {
			RemoteSyncRequest syncRequest = new RemoteSyncRequest(thisNode, currentHeader);
			logAndSend(MessageInTransit.newMessage(syncRequest, thisNode, node));
		}

		@Override
		public void sendSyncResponse(BFTNode node, DtoCommandsAndProof commandsAndProof) {
			RemoteSyncResponse syncResponse = new RemoteSyncResponse(thisNode, commandsAndProof);
			logAndSend(MessageInTransit.newMessage(syncResponse, thisNode, node));
		}

		private void logAndSend(MessageInTransit message) {
			log.debug("Send {}", message);
			receivedMessages.onNext(message);
		}

		private void checkArrivalAndLogMessage(MessageInTransit message) {
			long wantedArrival = message.timeCreated + message.delay;
			long timediff = System.currentTimeMillis() - wantedArrival;
			// timediff is not likely to be < 0, but just in case
			if (timediff < 0 || timediff >= LOG_WARN_DELAY) {
				log.warn("Message is {}ms late: {}", timediff, message);
			}
			log.debug("Receive {}", message);
		}

		private Observable<MessageInTransit> delayed(MessageInTransit message) {
			if (message.delay == 0) {
				return Observable.just(message);
			}
			return Observable.timer(message.delay, TimeUnit.MILLISECONDS)
		    	.flatMap(x -> Observable.just(message));
		}
	}

	public SimulatedNetworkImpl getNetwork(BFTNode forNode) {
		return receivers.computeIfAbsent(forNode, SimulatedNetworkImpl::new);
	}
}
