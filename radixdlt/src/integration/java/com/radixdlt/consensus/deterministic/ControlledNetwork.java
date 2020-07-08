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
import com.google.common.collect.ImmutableMap;
import com.radixdlt.EpochChangeSender;
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.EpochChange;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A BFT network supporting the EventCoordinatorNetworkSender interface which
 * stores each message in a queue until they are synchronously popped.
 *
 * This class is not thread safe.
 */
public final class ControlledNetwork {
	private final ImmutableList<ECPublicKey> nodes;
	private final ImmutableMap<ChannelId, LinkedList<ControlledMessage>> messageQueue;

	ControlledNetwork(ImmutableList<ECPublicKey> nodes) {
		this.nodes = nodes;
		this.messageQueue = nodes.stream()
			.flatMap(n0 -> nodes.stream().map(n1 -> new ChannelId(n0, n1)))
			.collect(
				ImmutableMap.toImmutableMap(
					key -> key,
					key -> new LinkedList<>()
				)
			);
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
			return sender.euid().toString().substring(0, 6) + " -> " + receiver.euid().toString().substring(0, 6);
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

	private void putMesssage(ControlledMessage controlledMessage) {
		messageQueue.get(controlledMessage.getChannelId()).add(controlledMessage);
	}

	public List<ControlledMessage> peekNextMessages() {
		return messageQueue.values()
			.stream()
			.filter(l -> !l.isEmpty())
			.map(LinkedList::getFirst)
			.collect(Collectors.toList());
	}

	public Object popNextMessage(ChannelId channelId) {
		return messageQueue.get(channelId).pop().getMsg();
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

	public ControlledSender getSender(ECPublicKey sender) {
		return new ControlledSender(sender);
	}

	public final class ControlledSender implements BFTEventSender, VertexStoreEventSender, SyncVerticesRPCSender, EpochChangeSender,
		CommittedStateSyncSender {
		private final ECPublicKey sender;

		private ControlledSender(ECPublicKey sender) {
			this.sender = sender;
		}

		@Override
		public void sendGetVerticesRequest(Hash id, ECPublicKey node, int count, Object opaque) {
			putMesssage(new ControlledMessage(sender, node, new ControlledGetVerticesRequest(id, count, sender, opaque)));
		}

		@Override
		public void sendGetVerticesResponse(GetVerticesRequest originalRequest, ImmutableList<Vertex> vertices) {
			ControlledGetVerticesRequest request = (ControlledGetVerticesRequest) originalRequest;
			GetVerticesResponse response = new GetVerticesResponse(request.getVertexId(), vertices, request.opaque);
			putMesssage(new ControlledMessage(sender, request.requestor, response));
		}

		@Override
		public void sendGetVerticesErrorResponse(GetVerticesRequest originalRequest, QuorumCertificate highestQC,
			QuorumCertificate highestCommittedQC) {
			ControlledGetVerticesRequest request = (ControlledGetVerticesRequest) originalRequest;
			GetVerticesErrorResponse response = new GetVerticesErrorResponse(request.getVertexId(), highestQC, highestCommittedQC, request.opaque);
			putMesssage(new ControlledMessage(sender, request.requestor, response));
		}

		@Override
		public void sendSyncedVertex(Vertex vertex) {
			putMesssage(new ControlledMessage(sender, sender, vertex.getId()));
		}

		@Override
		public void broadcastProposal(Proposal proposal, Set<ECPublicKey> nodes) {
			for (ECPublicKey receiver : nodes) {
				putMesssage(new ControlledMessage(sender, receiver, proposal));
			}
		}

		@Override
		public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
			putMesssage(new ControlledMessage(sender, newViewLeader, newView));
		}

		@Override
		public void sendVote(Vote vote, ECPublicKey leader) {
			putMesssage(new ControlledMessage(sender, leader, vote));
		}

		@Override
		public void epochChange(EpochChange epochChange) {
			putMesssage(new ControlledMessage(sender, sender, epochChange));
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
			putMesssage(new ControlledMessage(sender, sender, committedStateSync));
		}
	}
}
