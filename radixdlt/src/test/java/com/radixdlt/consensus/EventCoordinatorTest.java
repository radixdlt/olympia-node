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
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.QuorumRequirements;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.consensus.safety.SingleNodeQuorumRequirements;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Ints;
import java.util.Optional;
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
	private static final ECKeyPair SELF_KEY = makeKeyPair();
	private static final ECPublicKey SELF_PUB_KEY = SELF_KEY.getPublicKey();
	private static final RadixAddress SELF_ADDRESS = makeAddressFrom(SELF_PUB_KEY);

	private static AID makeAID(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return AID.from(temp);
	}

	private static ECKeyPair makeKeyPair() {
		try {
			return new ECKeyPair();
		} catch (CryptoException e) {
			throw new IllegalStateException("Unable to create key pair", e);
		}
	}

	private static RadixAddress makeAddressFrom(ECPublicKey pubKey) {
		Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(127); // no special significance
		return RadixAddress.from(universe, pubKey);
	}

	private static ECPublicKey makePubKey(EUID id) {
		ECPublicKey pubKey = mock(ECPublicKey.class);
		when(pubKey.getUID()).thenReturn(id);
		return pubKey;
	}

	@Test
	public void when_processing_vote_as_not_proposer__then_nothing_happens() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		QuorumRequirements quorumRequirements = new SingleNodeQuorumRequirements(SELF_ADDRESS.getUID());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		Vote voteMessage = mock(Vote.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(voteMessage.getVertexMetadata()).thenReturn(vertexMetadata);
		when(vertexMetadata.getView()).thenReturn(View.of(0L));
		when(proposerElection.isValidProposer(any(), any())).thenReturn(false);

		eventCoordinator.processVote(voteMessage);
		verify(safetyRules, times(0)).process(any(QuorumCertificate.class));
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
		PendingVotes pendingVotes = mock(PendingVotes.class);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		when(proposerElection.getProposer(any())).thenReturn(mock(EUID.class));
		when(mempool.getAtoms(anyInt(), any())).thenReturn(Lists.newArrayList());
		Vote vote = mock(Vote.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vote.getVertexMetadata()).thenReturn(vertexMetadata);
		when(vertexMetadata.getView()).thenReturn(View.of(0L));
		when(vertexMetadata.getId()).thenReturn(Hash.random());
		when(proposerElection.isValidProposer(any(), any())).thenReturn(true);
		when(pendingVotes.insertVote(eq(vote))).thenReturn(Optional.of(mock(QuorumCertificate.class)));
		when(pacemaker.getCurrentView()).thenReturn(mock(View.class));
		eventCoordinator.processVote(vote);
		verify(safetyRules, times(1)).process(any(QuorumCertificate.class));
		verify(pacemaker, times(1)).processQC(any());
	}

	@Test
	public void when_processing_relevant_local_timeout__then_new_view_is_emitted() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		QuorumRequirements quorumRequirements = new SingleNodeQuorumRequirements(SELF_ADDRESS.getUID());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		when(proposerElection.getProposer(any())).thenReturn(mock(EUID.class));
		when(pacemaker.processLocalTimeout(any())).thenReturn(true);
		when(pacemaker.getCurrentView()).thenReturn(View.of(1));
		eventCoordinator.processLocalTimeout(View.of(0L));
		verify(networkSender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_irrelevant_local_timeout__then_new_view_is_not_emitted() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		QuorumRequirements quorumRequirements = new SingleNodeQuorumRequirements(SELF_ADDRESS.getUID());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		when(pacemaker.processLocalTimeout(any())).thenReturn(false);
		eventCoordinator.processLocalTimeout(View.of(0L));
		verify(networkSender, times(0)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_remote_new_view_as_proposer__then_new_view_is_emitted() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		QuorumRequirements quorumRequirements = new SingleNodeQuorumRequirements(SELF_ADDRESS.getUID());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		NewView newView = mock(NewView.class);
		when(newView.getQc()).thenReturn(mock(QuorumCertificate.class));
		when(newView.getView()).thenReturn(View.of(0L));
		when(proposerElection.isValidProposer(any(), any())).thenReturn(true);
		when(proposerElection.getProposer(any())).thenReturn(mock(EUID.class));
		eventCoordinator.processRemoteNewView(newView);
		verify(pacemaker, times(1)).processRemoteNewView(any());
	}

	@Test
	public void when_processing_remote_new_view_as_not_proposer__then_new_view_is_not_emitted() {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		QuorumRequirements quorumRequirements = new SingleNodeQuorumRequirements(SELF_ADDRESS.getUID());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		NewView newView = mock(NewView.class);
		when(newView.getView()).thenReturn(View.of(0L));
		when(proposerElection.isValidProposer(any(), any())).thenReturn(false);
		eventCoordinator.processRemoteNewView(newView);
		verify(pacemaker, times(0)).processRemoteNewView(any());
	}

	@Test
	public void when_processing_invalid_proposal__then_atom_is_rejected() throws Exception {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		QuorumRequirements quorumRequirements = new SingleNodeQuorumRequirements(SELF_ADDRESS.getUID());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		View currentView = View.of(123);

		Vertex proposedVertex = mock(Vertex.class);
		Atom proposedAtom = mock(Atom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		when(proposedVertex.getQC()).thenReturn(mock(QuorumCertificate.class));
		when(proposedVertex.getView()).thenReturn(currentView);

		doThrow(new VertexInsertionException("Test", new RadixEngineException(RadixEngineErrorCode.CM_ERROR, DataPointer.ofAtom())))
			.when(vertexStore).insertVertex(any());
		when(pacemaker.processQC(any())).thenReturn(Optional.empty());
		when(pacemaker.getCurrentView()).thenReturn(currentView);
		eventCoordinator.processProposal(proposedVertex);
		verify(mempool, times(1)).removeRejectedAtom(eq(aid));
	}

	@Test
	public void when_processing_valid_stored_proposal__then_atom_is_voted_on_and_removed()
		throws SafetyViolationException {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		QuorumRequirements quorumRequirements = new SingleNodeQuorumRequirements(SELF_ADDRESS.getUID());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);
		ProposerElection proposerElection = mock(ProposerElection.class);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		View currentView = View.of(123);

		when(proposerElection.getProposer(any())).thenReturn(mock(EUID.class));

		Vertex proposedVertex = mock(Vertex.class);
		Atom proposedAtom = mock(Atom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		when(proposedVertex.getQC()).thenReturn(mock(QuorumCertificate.class));
		when(proposedVertex.getView()).thenReturn(currentView);
		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(eq(proposedVertex));

		when(pacemaker.processQC(any())).thenReturn(Optional.empty());
		eventCoordinator.processProposal(proposedVertex);

		verify(networkSender, times(1)).sendVote(eq(vote), any());
	}

	@Test
	public void when_processing_valid_stored_proposal_and_there_exists_a_new_commit__the_new_commit_atom_is_removed_from_mempool() throws Exception {
		ProposalGenerator proposalGenerator = mock(ProposalGenerator.class);
		Mempool mempool = mock(Mempool.class);
		EventCoordinatorNetworkSender networkSender = mock(EventCoordinatorNetworkSender.class);
		SafetyRules safetyRules = mock(SafetyRules.class);
		Pacemaker pacemaker = mock(Pacemaker.class);
		VertexStore vertexStore = mock(VertexStore.class);
		ProposerElection proposerElection = mock(ProposerElection.class);
		QuorumRequirements quorumRequirements = new SingleNodeQuorumRequirements(SELF_ADDRESS.getUID());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);

		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY);

		View currentView = View.of(123);

		when(pacemaker.processQC(any())).thenReturn(Optional.empty());
		when(pacemaker.getCurrentView()).thenReturn(currentView);

		Vertex proposalVertex = mock(Vertex.class);
		Hash proposalVertexId = mock(Hash.class);
		when(proposalVertex.getId()).thenReturn(proposalVertexId);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(mock(View.class));
		when(proposalVertex.getQC()).thenReturn(qc);
		when(proposalVertex.getView()).thenReturn(currentView);

		Hash committedVertexId = mock(Hash.class);
		Vertex committedVertex = mock(Vertex.class);
		Atom atom = mock(Atom.class);
		AID aid = mock(AID.class);
		when(atom.getAID()).thenReturn(aid);
		when(committedVertex.getAtom()).thenReturn(atom);

		when(safetyRules.process(eq(qc))).thenReturn(Optional.of(committedVertexId));
		when(vertexStore.commitVertex(eq(committedVertexId))).thenReturn(committedVertex);
		when(proposerElection.getProposer(any())).thenReturn(mock(EUID.class));

		eventCoordinator.processProposal(proposalVertex);
		verify(mempool, times(1)).removeCommittedAtom(eq(aid));
	}
}
