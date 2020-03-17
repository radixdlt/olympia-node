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

package com.radixdlt.consensus;

import com.google.common.collect.Lists;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.VoteResult;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.EventCoordinatorNetworkSender;
import com.radixdlt.utils.Ints;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventCoordinatorTest {
	private static final EUID SELF = EUID.ONE;

	private static AID makeAID(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return AID.from(temp);
	}

	@Test
	public void when_processing_vote_as_not_proposer__then_nothing_happens() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			proposerElection,
			SELF
		);

		Vote voteMessage = mock(Vote.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(voteMessage.getVertexMetadata()).thenReturn(vertexMetadata);
		when(vertexMetadata.getRound()).thenReturn(Round.of(0L));
		when(proposerElection.isValidProposer(any(), any())).thenReturn(false);

		eventCoordinator.processVote(voteMessage);
		verify(safetyRules, times(0)).process(any());
		verify(pacemaker, times(0)).processQC(any());
	}

	@Test
	public void when_processing_vote_as_a_proposer__then_components_are_notified() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			proposerElection,
			SELF
		);

		when(mempool.getAtoms(anyInt(), any())).thenReturn(Lists.newArrayList());
		Vote voteMessage = mock(Vote.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(voteMessage.getVertexMetadata()).thenReturn(vertexMetadata);
		when(vertexMetadata.getRound()).thenReturn(Round.of(0L));
		when(proposerElection.isValidProposer(any(), any())).thenReturn(true);

		eventCoordinator.processVote(voteMessage);
		verify(safetyRules, times(1)).process(any());
		verify(pacemaker, times(1)).processQC(any());
	}

	@Test
	public void when_processing_relevant_local_timeout__then_new_round_is_emitted() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			proposerElection,
			SELF
		);

		when(pacemaker.processLocalTimeout(any())).thenReturn(true);
		eventCoordinator.processLocalTimeout(Round.of(0L));
		verify(networkSender, times(1)).sendNewRound(any());
	}

	@Test
	public void when_processing_irrelevant_local_timeout__then_new_round_is_not_emitted() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			proposerElection,
			SELF
		);

		when(pacemaker.processLocalTimeout(any())).thenReturn(false);
		eventCoordinator.processLocalTimeout(Round.of(0L));
		verify(networkSender, times(0)).sendNewRound(any());
	}

	@Test
	public void when_processing_remote_new_round_as_proposer__then_new_round_is_emitted() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			proposerElection,
			SELF
		);

		NewRound newRound = mock(NewRound.class);
		when(newRound.getRound()).thenReturn(Round.of(0L));
		when(proposerElection.isValidProposer(any(), any())).thenReturn(true);
		eventCoordinator.processRemoteNewRound(newRound);
		verify(pacemaker, times(1)).processRemoteNewRound(any());
	}

	@Test
	public void when_processing_remote_new_round_as_not_proposer__then_new_round_is_not_emitted() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			proposerElection,
			SELF
		);

		NewRound newRound = mock(NewRound.class);
		when(newRound.getRound()).thenReturn(Round.of(0L));
		when(proposerElection.isValidProposer(any(), any())).thenReturn(false);
		eventCoordinator.processRemoteNewRound(newRound);
		verify(pacemaker, times(0)).processRemoteNewRound(any());
	}

	@Test
	public void when_processing_invalid_proposal__then_atom_is_rejected() throws Exception {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			proposerElection,
			SELF
		);

		Vertex proposedVertex = mock(Vertex.class);
		Atom proposedAtom = mock(Atom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		doThrow(new VertexInsertionException(new RadixEngineException(RadixEngineErrorCode.CM_ERROR, DataPointer.ofAtom())))
			.when(vertexStore).insertVertex(any());
		eventCoordinator.processProposal(proposedVertex);
		verify(mempool, times(1)).removeRejectedAtom(eq(aid));
	}

	@Test
	public void when_processing_valid_stored_proposal__then_atom_is_voted_on_and_removed() throws Exception {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			proposerElection,
			SELF
		);

		Vertex proposedVertex = mock(Vertex.class);
		Atom proposedAtom = mock(Atom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		VoteResult voteResult = mock(VoteResult.class);
		Vote voteMessage = mock(Vote.class);
		when(voteResult.getVote()).thenReturn(voteMessage);
		doReturn(voteResult).when(safetyRules).voteFor(eq(proposedVertex));
		eventCoordinator.processProposal(proposedVertex);

		verify(networkSender, times(1)).sendVote(eq(voteMessage));
		verify(mempool, times(1)).removeCommittedAtom(eq(aid));
	}
}
