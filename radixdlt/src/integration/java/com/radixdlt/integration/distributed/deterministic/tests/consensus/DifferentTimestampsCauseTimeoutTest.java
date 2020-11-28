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

package com.radixdlt.integration.distributed.deterministic.tests.consensus;

import com.google.inject.AbstractModule;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest.DeterministicManualExecutor;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

public class DifferentTimestampsCauseTimeoutTest {
	@Test
	public void when_four_nodes_receive_qcs_with_same_timestamps__quorum_is_achieved() {
		final int numNodes = 4;

		DeterministicManualExecutor executor = DeterministicTest.builder()
			.numNodes(numNodes)
			.messageMutator(mutateProposalsBy(0))
			.build()
			.createExecutor();

		executor.start();
		executeTwoViews(executor);

		executor.processNext(3, 3, ViewUpdate.class);
		executor.processNext(3, 3, Proposal.class);
		executor.processNext(3, 3, BFTUpdate.class);
		executor.processNext(3, 0, Proposal.class);
		executor.processNext(0, 0, ViewUpdate.class);
		executor.processNext(0, 0, BFTUpdate.class);
		executor.processNext(3, 1, Proposal.class);
		executor.processNext(1, 1, ViewUpdate.class);
		executor.processNext(1, 1, BFTUpdate.class);
		executor.processNext(3, 2, Proposal.class);
		executor.processNext(2, 2, ViewUpdate.class);
		executor.processNext(2, 2, BFTUpdate.class);
	}

	@Test
	public void when_four_nodes_receive_qcs_with_different_timestamps__quorum_is_not_achieved() {
		final int numNodes = 4;

		// TODO: this test isn't exactly right and should be updated so that
		// TODO: byzantine node sends different sets of valid QCs to each node
		DeterministicManualExecutor executor = DeterministicTest.builder()
			.overrideWithIncorrectModule(new AbstractModule() {
				@Override
				protected void configure() {
					bind(HashVerifier.class).toInstance((pubKey, hash, sig) -> true);
					bind(HashSigner.class).toInstance(h -> new ECDSASignature());
				}
			})
			.numNodes(numNodes)
			.messageMutator(mutateProposalsBy(1))
			.build()
			.createExecutor();

		executor.start();

		executeTwoViews(executor);

		// Timeouts from nodes
		executor.processNext(0, 0, ScheduledLocalTimeout.class);
		executor.processNext(1, 1, ScheduledLocalTimeout.class);
		executor.processNext(2, 2, ScheduledLocalTimeout.class);
		executor.processNext(3, 3, ScheduledLocalTimeout.class);
	}

	private void executeTwoViews(DeterministicManualExecutor executor) {
		// Proposal here has genesis qc, which has no timestamps
		executor.processNext(1, 1, ViewUpdate.class); // Messages to self first
		executor.processNext(1, 1, Proposal.class);
		executor.processNext(1, 1, BFTUpdate.class);
		executor.processNext(0, 0, ViewUpdate.class);
		executor.processNext(2, 2, ViewUpdate.class);
		executor.processNext(3, 3, ViewUpdate.class);
		executor.processNext(1, 0, Proposal.class);
		executor.processNext(0, 0, BFTUpdate.class);
		executor.processNext(1, 2, Proposal.class);
		executor.processNext(2, 2, BFTUpdate.class);
		executor.processNext(1, 3, Proposal.class);
		executor.processNext(3, 3, BFTUpdate.class);

		executor.processNext(2, 2, Vote.class); // Messages to self first
		executor.processNext(1, 2, Vote.class); // Leader votes early as it sees proposal early
		executor.processNext(0, 2, Vote.class);
		executor.processNext(3, 2, Vote.class);

		// Proposal here should have timestamps from previous view
		// They are mutated as required by the test
		executor.processNext(2, 2, ViewUpdate.class);
		executor.processNext(2, 2, Proposal.class);
		executor.processNext(2, 2, BFTUpdate.class);
		executor.processNext(2, 0, Proposal.class);
		executor.processNext(0, 0, ViewUpdate.class);
		executor.processNext(0, 0, BFTUpdate.class);
		executor.processNext(2, 1, Proposal.class);
		executor.processNext(1, 1, ViewUpdate.class);
		executor.processNext(1, 1, BFTUpdate.class);
		executor.processNext(2, 3, Proposal.class);
		executor.processNext(3, 3, ViewUpdate.class);
		executor.processNext(3, 3, BFTUpdate.class);

		executor.processNext(3, 3, Vote.class);
		executor.processNext(2, 3, Vote.class);
		executor.processNext(0, 3, Vote.class);
		executor.processNext(1, 3, Vote.class);
	}


	private MessageMutator mutateProposalsBy(int factor) {
		return (message, queue) -> {
			ControlledMessage messageToUse = message;
			Object msg = message.message();
			if (msg instanceof Proposal) {
				Proposal p = (Proposal) msg;
				int receiverIndex = message.channelId().receiverIndex();
				messageToUse = new ControlledMessage(
					message.origin(),
					message.channelId(),
					mutateProposal(p, receiverIndex * factor),
					message.arrivalTime()
				);
			}
			queue.add(messageToUse);
			return true;
		};
	}

	private Proposal mutateProposal(Proposal p, int destination) {
		QuorumCertificate committedQC = p.highQC().highestCommittedQC();
		BFTNode author = p.getAuthor();
		UnverifiedVertex vertex = p.getVertex();
		ECDSASignature signature = p.getSignature();

		return new Proposal(mutateVertex(vertex, destination), committedQC, author, signature);
	}

	private UnverifiedVertex mutateVertex(UnverifiedVertex v, int destination) {
		QuorumCertificate qc = v.getQC();
		View view = v.getView();
		Command command = v.getCommand();

		return new UnverifiedVertex(mutateQC(qc,  destination), view, command);
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
