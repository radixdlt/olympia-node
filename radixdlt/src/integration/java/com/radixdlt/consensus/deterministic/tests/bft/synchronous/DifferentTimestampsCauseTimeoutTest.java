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

package com.radixdlt.consensus.deterministic.tests.bft.synchronous;

import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.deterministic.DeterministicTest;

import java.util.Map;

import org.junit.Test;

public class DifferentTimestampsCauseTimeoutTest {

	@Test
	public void when_four_nodes_receive_qcs_with_same_timestamps__quorum_is_achieved() {
		final DeterministicTest test = DeterministicTest.createSingleEpochAlwaysSyncedWithTimeoutsTest(4);

		test.start();

		test.processNextMsg(0, 0, EpochChange.class);
		test.processNextMsg(1, 1, EpochChange.class);
		test.processNextMsg(2, 2, EpochChange.class);
		test.processNextMsg(3, 3, EpochChange.class);

		test.processNextMsg(1, 0, NewView.class);
		test.processNextMsg(1, 1, NewView.class);
		test.processNextMsg(1, 2, NewView.class);
		test.processNextMsg(1, 3, NewView.class);

		test.processNextMsg(0, 1, Proposal.class, p -> mutateProposal(p, 0));
		test.processNextMsg(1, 1, Proposal.class, p -> mutateProposal(p, 0));
		test.processNextMsg(2, 1, Proposal.class, p -> mutateProposal(p, 0));
		test.processNextMsg(3, 1, Proposal.class, p -> mutateProposal(p, 0));

		test.processNextMsg(1, 0, Vote.class);
		test.processNextMsg(1, 1, Vote.class);
		test.processNextMsg(1, 2, Vote.class);
		test.processNextMsg(1, 3, Vote.class);

		test.processNextMsg(2, 0, NewView.class);
		test.processNextMsg(2, 1, NewView.class);
		test.processNextMsg(2, 2, NewView.class);
		test.processNextMsg(2, 3, NewView.class);

		// Would be timeouts here if proposals were different
		test.processNextMsg(0, 2, Proposal.class);
		test.processNextMsg(1, 2, Proposal.class);
		test.processNextMsg(2, 2, Proposal.class);
		test.processNextMsg(3, 2, Proposal.class);
	}

	@Test
	public void when_four_nodes_receive_qcs_with_different_timestamps__quorum_is_not_achieved() {
		final DeterministicTest test = DeterministicTest.createSingleEpochAlwaysSyncedWithTimeoutsTest(4);

		test.start();

		test.processNextMsg(0, 0, EpochChange.class);
		test.processNextMsg(1, 1, EpochChange.class);
		test.processNextMsg(2, 2, EpochChange.class);
		test.processNextMsg(3, 3, EpochChange.class);

		test.processNextMsg(1, 0, NewView.class);
		test.processNextMsg(1, 1, NewView.class);
		test.processNextMsg(1, 2, NewView.class);
		test.processNextMsg(1, 3, NewView.class);

		test.processNextMsg(0, 1, Proposal.class, p -> mutateProposal(p, 0));
		test.processNextMsg(1, 1, Proposal.class, p -> mutateProposal(p, 1));
		test.processNextMsg(2, 1, Proposal.class, p -> mutateProposal(p, 2));
		test.processNextMsg(3, 1, Proposal.class, p -> mutateProposal(p, 3));

		test.processNextMsg(1, 0, Vote.class);
		test.processNextMsg(1, 1, Vote.class);
		test.processNextMsg(1, 2, Vote.class);
		test.processNextMsg(1, 3, Vote.class);

		test.processNextMsg(2, 0, NewView.class);
		test.processNextMsg(2, 1, NewView.class);
		test.processNextMsg(2, 2, NewView.class);
		test.processNextMsg(2, 3, NewView.class);

		// Timeouts from nodes
		test.processNextMsg(0, 0, LocalTimeout.class);
		test.processNextMsg(1, 1, LocalTimeout.class);
		// 2 (leader) will have already moved on to next view from the NewView messages
		test.processNextMsg(3, 3, LocalTimeout.class);
	}

	private Proposal mutateProposal(Proposal p, int destination) {
		QuorumCertificate committedQC = p.getCommittedQC();
		BFTNode author = p.getAuthor();
		Vertex vertex = p.getVertex();
		ECDSASignature signature = p.getSignature();

		return new Proposal(mutateVertex(vertex, destination), committedQC, author, signature, 0L);
	}

	private Vertex mutateVertex(Vertex v, int destination) {
		long epoch = v.getEpoch();
		QuorumCertificate qc = v.getQC();
		View view = v.getView();
		ClientAtom atom = v.getAtom();

		return new Vertex(epoch, mutateQC(qc,  destination), view, atom);
	}

	private QuorumCertificate mutateQC(QuorumCertificate qc, int destination) {
		TimestampedECDSASignatures signatures = qc.getTimestampedSignatures();
		VoteData voteData = qc.getVoteData();

		return new QuorumCertificate(voteData, mutateTimestampedSignatures(signatures, destination));
	}

	private TimestampedECDSASignatures mutateTimestampedSignatures(TimestampedECDSASignatures signatures, int destination) {
		Map<BFTNode, TimestampedECDSASignature> sigs = signatures.getSignatures();
		return new TimestampedECDSASignatures(sigs.entrySet().stream()
			.map(e -> Pair.of(e.getKey(), mutateTimestampedSignature(e.getValue(), destination)))
			.collect(ImmutableMap.toImmutableMap(Pair::getFirst, Pair::getSecond)));
	}

	private TimestampedECDSASignature mutateTimestampedSignature(TimestampedECDSASignature signature, int destination) {
		long timestamp = signature.timestamp();
		UInt256 weight = signature.weight();
		ECDSASignature sig = signature.signature();

		return TimestampedECDSASignature.from(timestamp + destination, weight, sig);
	}
}
