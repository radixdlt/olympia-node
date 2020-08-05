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

package com.radixdlt.consensus.deterministic;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.VertexStore.SyncedVertexSender;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.Vote;
import com.radixdlt.syncer.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.crypto.Hash;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A BFT network supporting the EventCoordinatorNetworkSender interface which
 * stores each message in a queue until they are synchronously popped.
 *
 * This class is not thread safe.
 */
public final class ControlledNetwork {
	// Process sync related messages before consensus messages
	private static final MessageRank EARLIEST_POSSIBLE = new MessageRank(0L, 0L);

	private final MessageQueue messageQueue = new MessageQueue();

	ControlledNetwork() {
		// Nothing here right now
	}

	// Message rank.  Used to implement timeouts.
	// Messages in a particular ranking are processed in arrival order, but
	// timeouts in particular will be put into the next rank to ensure
	// that they are processed after the current rank. The rank is changed
	// for each node whenever a new view or epoch change is seen.
	static final class MessageRank implements Comparable<MessageRank> {
		private static final Comparator<MessageRank> COMPARATOR =
			Comparator.comparingLong((MessageRank eav) -> eav.epoch).thenComparingLong(eav -> eav.view);
		final long epoch;
		final long view;

		MessageRank(long epoch, long view) {
			this.epoch = epoch;
			this.view = view;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(this.epoch) * 31 + Long.hashCode(this.view);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof MessageRank)) {
				return false;
			}
			MessageRank that = (MessageRank) o;
			return this.epoch == that.epoch && this.view == that.view;
		}

		@Override
		public int compareTo(MessageRank that) {
			return COMPARATOR.compare(this, that);
		}

		@Override
		public String toString() {
			return String.format("[%s:%s]", this.epoch, this.view);
		}
	}

	static final class ChannelId {
		private final BFTNode sender;
		private final BFTNode receiver;

		ChannelId(BFTNode sender, BFTNode receiver) {
			this.sender = sender;
			this.receiver = receiver;
		}

		public BFTNode getReceiver() {
			return receiver;
		}

		public BFTNode getSender() {
			return sender;
		}

		@Override
		public int hashCode() {
			return Objects.hash(sender, receiver);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ChannelId)) {
				return false;
			}

			ChannelId other = (ChannelId) obj;
			return this.sender.equals(other.sender) && this.receiver.equals(other.receiver);
		}

		@Override
		public String toString() {
			return sender.getSimpleName() + "->" + receiver.getSimpleName();
		}
	}

	static final class ControlledMessage {
		private final ChannelId channelId;
		private final Object msg;

		ControlledMessage(BFTNode sender, BFTNode receiver, Object msg) {
			this.channelId = new ChannelId(sender, receiver);
			this.msg = msg;
		}

		public ChannelId getChannelId() {
			return channelId;
		}

		public Object getMsg() {
			return msg;
		}

		@Override
		public String toString() {
			return channelId + " " + msg;
		}
	}

	public List<ControlledMessage> peekNextMessages() {
		return this.messageQueue.lowestViewMessages();
	}

	public Object popNextMessage(ChannelId channelId) {
		ControlledMessage controlledMessage = this.messageQueue.pop(channelId);
		return controlledMessage.msg;
	}

	private static class ControlledGetVerticesRequest implements GetVerticesRequest {
		private final Hash id;
		private final int count;
		private final Object opaque;
		private final BFTNode requestor;

		private ControlledGetVerticesRequest(Hash id, int count, BFTNode requestor, Object opaque) {
			this.id = id;
			this.count = count;
			this.requestor = requestor;
			this.opaque = opaque;
		}

		@Override
		public Hash getVertexId() {
			return id;
		}

		@Override
		public int getCount() {
			return count;
		}

		@Override
		public String toString() {
			return String.format("%s{count=%s}", this.getClass().getSimpleName(), count);
		}
	}

	public ControlledSender createSender(BFTNode sender) {
		return new ControlledSender(sender);
	}

	public final class ControlledSender implements BFTEventSender, VertexStoreEventSender, SyncVerticesRPCSender, SyncedVertexSender,
		EpochChangeSender, CommittedStateSyncSender, LocalTimeoutSender {
		private final BFTNode sender;

		private ControlledSender(BFTNode sender) {
			this.sender = sender;
		}

		@Override
		public void sendGetVerticesRequest(Hash id, BFTNode node, int count, Object opaque) {
			putMessage(EARLIEST_POSSIBLE, new ControlledMessage(sender, node, new ControlledGetVerticesRequest(id, count, sender, opaque)));
		}

		@Override
		public void sendGetVerticesResponse(GetVerticesRequest originalRequest, ImmutableList<Vertex> vertices) {
			ControlledGetVerticesRequest request = (ControlledGetVerticesRequest) originalRequest;
			GetVerticesResponse response = new GetVerticesResponse(request.getVertexId(), vertices, request.opaque);
			putMessage(EARLIEST_POSSIBLE, new ControlledMessage(sender, request.requestor, response));
		}

		@Override
		public void sendGetVerticesErrorResponse(GetVerticesRequest originalRequest, QuorumCertificate highestQC,
			QuorumCertificate highestCommittedQC) {
			ControlledGetVerticesRequest request = (ControlledGetVerticesRequest) originalRequest;
			GetVerticesErrorResponse response = new GetVerticesErrorResponse(request.getVertexId(), highestQC, highestCommittedQC, request.opaque);
			putMessage(EARLIEST_POSSIBLE, new ControlledMessage(sender, request.requestor, response));
		}

		@Override
		public void sendSyncedVertex(Vertex vertex) {
			putMessage(EARLIEST_POSSIBLE, new ControlledMessage(sender, sender, vertex.getId()));
		}

		@Override
		public void broadcastProposal(Proposal proposal, Set<BFTNode> nodes) {
			MessageRank rank = messageRank(proposal);
			for (BFTNode receiver : nodes) {
				putMessage(rank, new ControlledMessage(sender, receiver, proposal));
			}
		}

		@Override
		public void sendNewView(NewView newView, BFTNode newViewLeader) {
			putMessage(messageRank(newView), new ControlledMessage(sender, newViewLeader, newView));
		}

		@Override
		public void sendVote(Vote vote, BFTNode leader) {
			putMessage(messageRank(vote.getVoteData().getProposed(), 0), new ControlledMessage(sender, leader, vote));
		}

		@Override
		public void epochChange(EpochChange epochChange) {
			putMessage(messageRank(epochChange), new ControlledMessage(sender, sender, epochChange));
		}

		@Override
		public void sendCommittedVertex(Vertex vertex) {
			// Ignore committed vertex signal
		}

		@Override
		public void highQC(QuorumCertificate qc) {
			// Ignore high QC signal
		}

		@Override
		public void sendCommittedStateSync(long stateVersion, Object opaque) {
			CommittedStateSync committedStateSync = new CommittedStateSync(stateVersion, opaque);
			putMessage(EARLIEST_POSSIBLE, new ControlledMessage(sender, sender, committedStateSync));
		}

		@Override
		public void scheduleTimeout(LocalTimeout localTimeout, long milliseconds) {
			putMessage(messageRank(localTimeout), new ControlledMessage(sender, sender, localTimeout));
		}

		private void putMessage(MessageRank eav, ControlledMessage controlledMessage) {
			ControlledNetwork.this.messageQueue.add(eav, controlledMessage);
		}

		private MessageRank messageRank(EpochChange epochChange) {
			// Last message in this epoch
			return new MessageRank(epochChange.getAncestor().getEpoch(), Long.MAX_VALUE);
		}

		private MessageRank messageRank(NewView newView) {
			return new MessageRank(newView.getEpoch(), newView.getView().number());
		}

		private MessageRank messageRank(Proposal proposal) {
			return new MessageRank(proposal.getEpoch(), proposal.getVertex().getView().number());
		}

		private MessageRank messageRank(VertexMetadata metadata, long viewIncrement) {
			return new MessageRank(metadata.getEpoch(), metadata.getView().number() + viewIncrement);
		}

		private MessageRank messageRank(LocalTimeout localTimeout) {
			return new MessageRank(localTimeout.getEpoch(), localTimeout.getView().number() + 2);
		}
	}
}
