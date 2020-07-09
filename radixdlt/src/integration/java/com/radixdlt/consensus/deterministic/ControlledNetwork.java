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
import com.radixdlt.EpochChangeSender;
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.EpochChange;
import com.radixdlt.consensus.LocalTimeout;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.crypto.ECPublicKey;
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
	private final MessageQueue messageQueue = new MessageQueue();

	ControlledNetwork() {
		// Nothing here right now
	}

	static final class EpochAndView implements Comparable<EpochAndView> {
		private static final Comparator<EpochAndView> COMPARATOR =
			Comparator.comparingLong((EpochAndView eav) -> eav.epoch).thenComparing(eav -> eav.view);
		final long epoch;
		final View view;

		EpochAndView(long epoch, View view) {
			this.epoch = epoch;
			this.view = view;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(this.epoch) * 31 + view.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof EpochAndView)) {
				return false;
			}
			EpochAndView that = (EpochAndView) o;
			return this.epoch == that.epoch && Objects.equals(this.view, that.view);
		}

		@Override
		public int compareTo(EpochAndView that) {
			return COMPARATOR.compare(this, that);
		}

		@Override
		public String toString() {
			return String.format("[%s:%s]", this.epoch, this.view);
		}
	}

	static final class ChannelId {
		private final ECPublicKey sender;
		private final ECPublicKey receiver;

		ChannelId(ECPublicKey sender, ECPublicKey receiver) {
			this.sender = sender;
			this.receiver = receiver;
		}

		public ECPublicKey getReceiver() {
			return receiver;
		}

		public ECPublicKey getSender() {
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
			return sender.euid().toString().substring(0, 6) + "->" + receiver.euid().toString().substring(0, 6);
		}
	}

	static final class ControlledMessage {
		private final ChannelId channelId;
		private final Object msg;

		ControlledMessage(ECPublicKey sender, ECPublicKey receiver, Object msg) {
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

	private void putMessage(EpochAndView eav, ControlledMessage controlledMessage) {
		this.messageQueue.add(eav, controlledMessage);
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
		private final ECPublicKey requestor;

		private ControlledGetVerticesRequest(Hash id, int count, ECPublicKey requestor, Object opaque) {
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

	public ControlledSender createSender(ECPublicKey sender) {
		return new ControlledSender(sender);
	}

	public final class ControlledSender implements BFTEventSender, VertexStoreEventSender, SyncVerticesRPCSender, EpochChangeSender,
		CommittedStateSyncSender, LocalTimeoutSender {
		private final ECPublicKey sender;
		private EpochAndView currentEpochAndView = new EpochAndView(1L, View.genesis());

		private ControlledSender(ECPublicKey sender) {
			this.sender = sender;
		}

		@Override
		public void sendGetVerticesRequest(Hash id, ECPublicKey node, int count, Object opaque) {
			putMessage(this.currentEpochAndView, new ControlledMessage(sender, node, new ControlledGetVerticesRequest(id, count, sender, opaque)));
		}

		@Override
		public void sendGetVerticesResponse(GetVerticesRequest originalRequest, ImmutableList<Vertex> vertices) {
			ControlledGetVerticesRequest request = (ControlledGetVerticesRequest) originalRequest;
			GetVerticesResponse response = new GetVerticesResponse(request.getVertexId(), vertices, request.opaque);
			putMessage(this.currentEpochAndView, new ControlledMessage(sender, request.requestor, response));
		}

		@Override
		public void sendGetVerticesErrorResponse(GetVerticesRequest originalRequest, QuorumCertificate highestQC,
			QuorumCertificate highestCommittedQC) {
			ControlledGetVerticesRequest request = (ControlledGetVerticesRequest) originalRequest;
			GetVerticesErrorResponse response = new GetVerticesErrorResponse(request.getVertexId(), highestQC, highestCommittedQC, request.opaque);
			putMessage(this.currentEpochAndView, new ControlledMessage(sender, request.requestor, response));
		}

		@Override
		public void sendSyncedVertex(Vertex vertex) {
			putMessage(this.currentEpochAndView, new ControlledMessage(sender, sender, vertex.getId()));
		}

		@Override
		public void broadcastProposal(Proposal proposal, Set<ECPublicKey> nodes) {
			for (ECPublicKey receiver : nodes) {
				putMessage(this.currentEpochAndView, new ControlledMessage(sender, receiver, proposal));
			}
		}

		@Override
		public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
			putMessage(this.currentEpochAndView, new ControlledMessage(sender, newViewLeader, newView));
            this.currentEpochAndView = new EpochAndView(currentEpochAndView.epoch, newView.getView());
		}

		@Override
		public void sendVote(Vote vote, ECPublicKey leader) {
			putMessage(this.currentEpochAndView, new ControlledMessage(sender, leader, vote));
		}

		@Override
		public void epochChange(EpochChange epochChange) {
			putMessage(this.currentEpochAndView, new ControlledMessage(sender, sender, epochChange));
			this.currentEpochAndView = new EpochAndView(epochChange.getAncestor().getEpoch() + 1, View.genesis());
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
			putMessage(this.currentEpochAndView, new ControlledMessage(sender, sender, committedStateSync));
		}

        @Override
        public void scheduleTimeout(LocalTimeout localTimeout, long milliseconds) {
        	putMessage(new EpochAndView(localTimeout.getEpoch(), localTimeout.getView().next()), new ControlledMessage(sender, sender, localTimeout));
        }
	}
}
