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
import com.radixdlt.consensus.epoch.LocalViewUpdate;
import java.util.LinkedList;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
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
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import com.radixdlt.environment.deterministic.network.ChannelId;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.fail;

public class DifferentTimestampsCauseTimeoutTest {
	@Test
	public void when_four_nodes_receive_qcs_with_same_timestamps__quorum_is_achieved() {
		final int numNodes = 4;

		LinkedList<Pair<ChannelId, Class<?>>> processingSequence = Lists.newLinkedList();

		addTwoViews(processingSequence);

		processingSequence.add(Pair.of(ChannelId.of(3, 3), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(0, 0), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(0, 0), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), BFTUpdate.class));

		DeterministicTest.builder()
			.numNodes(numNodes)
			.messageSelector(sequenceSelector(processingSequence))
			.messageMutator(mutateProposalsBy(0))
			.build()
			.runForCount(processingSequence.size());
	}

	@Test
	public void when_four_nodes_receive_qcs_with_different_timestamps__quorum_is_not_achieved() {
		final int numNodes = 4;

		LinkedList<Pair<ChannelId, Class<?>>> processingSequence = Lists.newLinkedList();

		addTwoViews(processingSequence);

		// Timeouts from nodes
		processingSequence.add(Pair.of(ChannelId.of(0, 0), LocalTimeout.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), LocalTimeout.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), LocalTimeout.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), LocalTimeout.class));

		// TODO: this test isn't exactly right and should be updated so that
		// TODO: byzantine node sends different sets of valid QCs to each node
		DeterministicTest.builder()
			.overrideWithIncorrectModule(new AbstractModule() {
				@Override
				protected void configure() {
					bind(HashVerifier.class).toInstance((pubKey, hash, sig) -> true);
					bind(HashSigner.class).toInstance(h -> new ECDSASignature());
				}
			})
			.numNodes(numNodes)
			.messageSelector(sequenceSelector(processingSequence))
			.messageMutator(mutateProposalsBy(1))
			.build()
			.runForCount(processingSequence.size());
	}

	private void addTwoViews(LinkedList<Pair<ChannelId, Class<?>>> processingSequence) {
		// Proposal here has genesis qc, which has no timestamps
		processingSequence.add(Pair.of(ChannelId.of(1, 1), LocalViewUpdate.class)); // Messages to self first
		processingSequence.add(Pair.of(ChannelId.of(1, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(0, 0), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(0, 0), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 3), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), BFTUpdate.class));

		processingSequence.add(Pair.of(ChannelId.of(2, 2), Vote.class)); // Messages to self first
		processingSequence.add(Pair.of(ChannelId.of(1, 2), Vote.class)); // Leader votes early as it sees proposal early
		processingSequence.add(Pair.of(ChannelId.of(0, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), Vote.class));

		// Proposal here should have timestamps from previous view
		// They are mutated as required by the test
		processingSequence.add(Pair.of(ChannelId.of(2, 2), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(0, 0), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(0, 0), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 3), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), LocalViewUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), BFTUpdate.class));

		processingSequence.add(Pair.of(ChannelId.of(3, 3), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 3), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(0, 3), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 3), Vote.class));
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

	private MessageSelector sequenceSelector(LinkedList<Pair<ChannelId, Class<?>>> processingSequence) {
		return messages -> {
			if (processingSequence.isEmpty()) {
				// We have finished
				return null;
			}
			final Pair<ChannelId, Class<?>> messageDetails = processingSequence.pop();
			final ChannelId channel = messageDetails.getFirst();
			final Class<?> msgClass = messageDetails.getSecond();
			for (ControlledMessage message : messages) {
				if (channel.equals(message.channelId())
					&& msgClass.isAssignableFrom(message.message().getClass())) {
					return message;
				}
			}
			fail(String.format("Can't find %s %s message in: %s", channel, msgClass.getSimpleName(), messages));
			return null; // Not required, but compiler can't tell that fail throws exception
		};
	}
}
