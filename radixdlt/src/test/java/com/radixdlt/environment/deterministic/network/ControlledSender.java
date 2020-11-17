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

package com.radixdlt.environment.deterministic.network;

import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.epoch.LocalViewUpdate;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork.DeterministicSender;

/**
 * A sender within a deterministic network.
 */
public final class ControlledSender implements DeterministicSender {
	private final DeterministicNetwork network;
	private final BFTNode self;
	private final int senderIndex;
	private final ChannelId localChannel;


	ControlledSender(DeterministicNetwork network, BFTNode self, int senderIndex) {
		this.network = network;
		this.self = self;
		this.senderIndex = senderIndex;
		this.localChannel = ChannelId.of(this.senderIndex, this.senderIndex);
	}

	@Override
	public void sendGetVerticesRequest(BFTNode node, LocalGetVerticesRequest localRequest) {
		GetVerticesRequest request = new GetVerticesRequest(self, localRequest.getVertexId(), localRequest.getCount());
		ChannelId channelId = ChannelId.of(this.senderIndex, this.network.lookup(node));
		handleMessage(new ControlledMessage(channelId, request, arrivalTime(channelId)));
	}

	@Override
	public void sendGetVerticesResponse(BFTNode node, ImmutableList<VerifiedVertex> vertices) {
		GetVerticesResponse response = new GetVerticesResponse(self, vertices);
		ChannelId channelId = ChannelId.of(this.senderIndex, this.network.lookup(node));
		handleMessage(new ControlledMessage(channelId, response, arrivalTime(channelId)));
	}

	@Override
	public void sendGetVerticesErrorResponse(BFTNode node, HighQC highQC) {
		GetVerticesErrorResponse response = new GetVerticesErrorResponse(this.self, highQC);
		ChannelId channelId = ChannelId.of(this.senderIndex, this.network.lookup(node));
		handleMessage(new ControlledMessage(channelId, response, arrivalTime(channelId)));
	}

	@Override
	public void sendBFTUpdate(BFTUpdate update) {
		handleMessage(new ControlledMessage(this.localChannel, update, arrivalTime(this.localChannel)));
	}

	@Override
	public void broadcastProposal(Proposal proposal, Set<BFTNode> nodes) {
		for (BFTNode node : nodes) {
			int receiverIndex = this.network.lookup(node);
			ChannelId channelId = ChannelId.of(this.senderIndex, receiverIndex);
			handleMessage(new ControlledMessage(channelId, proposal, arrivalTime(channelId)));
		}
	}

	@Override
	public void broadcastViewTimeout(ViewTimeout viewTimeout, Set<BFTNode> nodes) {
		for (BFTNode node : nodes) {
			ChannelId channelId = ChannelId.of(this.senderIndex, this.network.lookup(node));
			handleMessage(new ControlledMessage(channelId, viewTimeout, arrivalTime(channelId)));
		}
	}

	@Override
	public void sendVote(Vote vote, BFTNode nextLeader) {
		ChannelId channelId = ChannelId.of(this.senderIndex, this.network.lookup(nextLeader));
		handleMessage(new ControlledMessage(channelId, vote, arrivalTime(channelId)));
	}

	@Override
	public void sendCommitted(BFTCommittedUpdate update) {
		// Ignore committed vertex signal
	}

	@Override
	public void highQC(QuorumCertificate qc) {
		// Ignore high QC signal
	}

	@Override
	public void scheduleTimeout(LocalTimeout localTimeout, long milliseconds) {
		ControlledMessage msg = new ControlledMessage(this.localChannel, localTimeout, arrivalTime(this.localChannel) + milliseconds);
		handleMessage(msg);
	}

	@Override
	public void sendGetEpochRequest(BFTNode node, long epoch) {
		// Ignore get epoch requests for now
	}

	@Override
	public void sendGetEpochResponse(BFTNode node, VerifiedLedgerHeaderAndProof ancestor) {
		GetEpochResponse getEpochResponse = new GetEpochResponse(node, ancestor);
		ChannelId channelId = ChannelId.of(this.senderIndex, this.network.lookup(node));
		handleMessage(new ControlledMessage(channelId, getEpochResponse, arrivalTime(channelId)));
	}

	@Override
	public void sendLedgerUpdate(EpochsLedgerUpdate epochsLedgerUpdate) {
		handleMessage(new ControlledMessage(this.localChannel, epochsLedgerUpdate, arrivalTime(this.localChannel)));
	}

	@Override
	public void scheduleTimeout(LocalGetVerticesRequest request, long milliseconds) {
		// Ignore bft sync timeouts
	}

	@Override
	public void sendLocalViewUpdate(LocalViewUpdate viewUpdate) {
		handleMessage(new ControlledMessage(this.localChannel, viewUpdate, arrivalTime(this.localChannel)));
	}

	private void handleMessage(ControlledMessage controlledMessage) {
		this.network.handleMessage(controlledMessage);
	}

	private long arrivalTime(ChannelId channelId) {
		long delay = this.network.delayForChannel(channelId);
		return this.network.currentTime() + delay;
	}
}
