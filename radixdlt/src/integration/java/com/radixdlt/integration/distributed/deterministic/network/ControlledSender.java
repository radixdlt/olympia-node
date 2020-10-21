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

package com.radixdlt.integration.distributed.deterministic.network;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.integration.distributed.deterministic.network.DeterministicNetwork.DeterministicSender;

/**
 * A sender within a deterministic network.
 */
public final class ControlledSender implements DeterministicSender {
	private final DeterministicNetwork network;
	private final BFTNode self;
	private final int senderIndex;

	ControlledSender(DeterministicNetwork network, BFTNode self, int senderIndex) {
		this.network = network;
		this.self = self;
		this.senderIndex = senderIndex;
	}

	@Override
	public void sendGetVerticesRequest(BFTNode node, HashCode id, int count) {
		GetVerticesRequest request = new GetVerticesRequest(self, id, count);
		int receiver = this.network.lookup(node);
		handleMessage(MessageRank.EARLIEST_POSSIBLE, new ControlledMessage(this.senderIndex, receiver, request));
	}

	@Override
	public void sendGetVerticesResponse(BFTNode node, ImmutableList<VerifiedVertex> vertices) {
		GetVerticesResponse response = new GetVerticesResponse(self, vertices);
		int receiver = this.network.lookup(node);
		handleMessage(MessageRank.EARLIEST_POSSIBLE, new ControlledMessage(this.senderIndex, receiver, response));
	}

	@Override
	public void sendGetVerticesErrorResponse(BFTNode node, HighQC syncInfo) {
		GetVerticesErrorResponse response = new GetVerticesErrorResponse(this.self, syncInfo);
		int receiver = this.network.lookup(node);
		handleMessage(MessageRank.EARLIEST_POSSIBLE, new ControlledMessage(this.senderIndex, receiver, response));
	}

	@Override
	public void sendBFTUpdate(BFTUpdate update) {
		handleMessage(MessageRank.EARLIEST_POSSIBLE, new ControlledMessage(this.senderIndex, this.senderIndex, update));
	}

	@Override
	public void broadcastProposal(Proposal proposal, Set<BFTNode> nodes) {
		MessageRank rank = messageRank(proposal);
		for (BFTNode node : nodes) {
			int receiver = this.network.lookup(node);
			handleMessage(rank, new ControlledMessage(this.senderIndex, receiver, proposal));
		}
	}

	@Override
	public void sendNewView(NewView newView, BFTNode newViewLeader) {
		int receiver = this.network.lookup(newViewLeader);
		handleMessage(messageRank(newView), new ControlledMessage(this.senderIndex, receiver, newView));
	}

	@Override
	public void sendVote(Vote vote, BFTNode leader) {
		int receiver = this.network.lookup(leader);
		handleMessage(messageRank(vote.getVoteData().getProposed(), 0), new ControlledMessage(this.senderIndex, receiver, vote));
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
		handleMessage(messageRank(localTimeout), new ControlledMessage(this.senderIndex, this.senderIndex, localTimeout));
	}

	@Override
	public void sendGetEpochRequest(BFTNode node, long epoch) {
		// TODO: Implement
		throw new UnsupportedOperationException();
	}

	@Override
	public void sendGetEpochResponse(BFTNode node, VerifiedLedgerHeaderAndProof ancestor) {
		GetEpochResponse getEpochResponse = new GetEpochResponse(node, ancestor);
		handleMessage(messageRank(getEpochResponse), new ControlledMessage(this.senderIndex, this.network.lookup(node), getEpochResponse));
	}

	@Override
	public void sendLedgerUpdate(EpochsLedgerUpdate epochsLedgerUpdate) {
		handleMessage(messageRank(epochsLedgerUpdate), new ControlledMessage(this.senderIndex, this.senderIndex, epochsLedgerUpdate));
	}

	private void handleMessage(MessageRank eav, ControlledMessage controlledMessage) {
		this.network.handleMessage(eav, controlledMessage);
	}

	private MessageRank messageRank(GetEpochResponse getEpochResponse) {
		VerifiedLedgerHeaderAndProof proof = getEpochResponse.getEpochProof();
		return MessageRank.of(proof.getEpoch(), proof.getView().number() + 3);
	}

	private MessageRank messageRank(EpochsLedgerUpdate epochsLedgerUpdate) {
		VerifiedLedgerHeaderAndProof proof = epochsLedgerUpdate.getTail();
		return MessageRank.of(proof.getEpoch(), proof.getView().number() + 3);
	}

	private MessageRank messageRank(NewView newView) {
		return MessageRank.of(newView.getEpoch(), newView.getView().number());
	}

	private MessageRank messageRank(Proposal proposal) {
		return MessageRank.of(proposal.getEpoch(), proposal.getVertex().getView().number());
	}

	private MessageRank messageRank(BFTHeader header, long viewIncrement) {
		return MessageRank.of(header.getLedgerHeader().getEpoch(), header.getView().number() + viewIncrement);
	}

	private MessageRank messageRank(LocalTimeout localTimeout) {
		return MessageRank.of(localTimeout.getEpoch(), localTimeout.getView().number() + 2);
	}
}
