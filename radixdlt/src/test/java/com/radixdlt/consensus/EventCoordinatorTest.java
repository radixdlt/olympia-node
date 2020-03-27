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
import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.Ints;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventCoordinatorTest {
	private static final ECKeyPair SELF_KEY = ECKeyPair.generateNew();

	private ValidatingEventCoordinator eventCoordinator;
	private ProposalGenerator proposalGenerator;
	private ProposerElection proposerElection;
	private SafetyRules safetyRules;
	private Pacemaker pacemaker;
	private PendingVotes pendingVotes;
	private Mempool mempool;
	private EventCoordinatorNetworkSender networkSender;
	private VertexStore vertexStore;
	private ValidatorSet validatorSet;

	@Before
	public void setUp() {
		this.proposalGenerator = mock(ProposalGenerator.class);
		this.mempool = mock(Mempool.class);
		this.networkSender = mock(EventCoordinatorNetworkSender.class);
		this.safetyRules = mock(SafetyRules.class);
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStore = mock(VertexStore.class);
		this.pendingVotes = mock(PendingVotes.class);
		this.proposerElection = mock(ProposerElection.class);
		this.validatorSet = mock(ValidatorSet.class);

		this.eventCoordinator = new ValidatingEventCoordinator(
			proposalGenerator,
			mempool,
			networkSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			SELF_KEY,
			validatorSet
		);
	}

	private static AID makeAID(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return AID.from(temp);
	}

	@Test
	public void when_processing_vote_as_not_proposer__then_nothing_happens() {
		Vote voteMessage = mock(Vote.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(voteMessage.getVertexMetadata()).thenReturn(vertexMetadata);
		when(vertexMetadata.getView()).thenReturn(View.of(0L));

		eventCoordinator.processVote(voteMessage);
		verify(safetyRules, times(0)).process(any(QuorumCertificate.class));
		verify(pacemaker, times(0)).processQC(any());
	}

	@Test
	public void when_processing_vote_as_a_proposer_and_quorum_is_reached__then_a_new_view_is_sent() {
		when(proposerElection.getProposer(any())).thenReturn(SELF_KEY.getPublicKey());

		Vote vote = mock(Vote.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vote.getVertexMetadata()).thenReturn(vertexMetadata);
		when(vertexMetadata.getView()).thenReturn(View.of(0L));
		when(vertexMetadata.getId()).thenReturn(Hash.random());

		QuorumCertificate qc = mock(QuorumCertificate.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		when(pendingVotes.insertVote(eq(vote), any())).thenReturn(Optional.of(qc));
		when(mempool.getAtoms(anyInt(), any())).thenReturn(Lists.newArrayList());
		when(pacemaker.getCurrentView()).thenReturn(mock(View.class));
		when(pacemaker.processQC(eq(view))).thenReturn(Optional.of(mock(View.class)));

		eventCoordinator.processVote(vote);

		verify(networkSender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_relevant_local_timeout__then_new_view_is_emitted() {
		when(proposerElection.getProposer(any())).thenReturn(ECKeyPair.generateNew().getPublicKey());
		when(pacemaker.processLocalTimeout(any())).thenReturn(Optional.of(View.of(1)));
		when(pacemaker.getCurrentView()).thenReturn(View.of(1));
		eventCoordinator.processLocalTimeout(View.of(0L));
		verify(networkSender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_irrelevant_local_timeout__then_new_view_is_not_emitted() {
		when(pacemaker.processLocalTimeout(any())).thenReturn(Optional.empty());
		eventCoordinator.processLocalTimeout(View.of(0L));
		verify(networkSender, times(0)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_new_view_as_proposer__then_new_view_is_emitted() {
		NewView newView = mock(NewView.class);
		when(newView.getQC()).thenReturn(mock(QuorumCertificate.class));
		when(newView.getView()).thenReturn(View.of(0L));
		when(proposerElection.getProposer(any())).thenReturn(SELF_KEY.getPublicKey());
		eventCoordinator.processNewView(newView);
		verify(pacemaker, times(1)).processNewView(any(), any());
	}

	@Test
	public void when_processing_new_view_as_not_proposer__then_new_view_is_not_emitted() {
		NewView newView = mock(NewView.class);
		when(newView.getView()).thenReturn(View.of(0L));
		eventCoordinator.processNewView(newView);
		verify(pacemaker, times(0)).processNewView(any(), any());
	}

	@Test
	public void when_processing_old_proposal__then_no_vertex_is_inserted() throws Exception {
		when(pacemaker.getCurrentView()).thenReturn(View.of(10));

		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(9));
		eventCoordinator.processProposal(vertex);
		verify(vertexStore, never()).insertVertex(any());
	}

	@Test
	public void when_processing_invalid_proposal__then_atom_is_rejected() throws Exception {
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
		View currentView = View.of(123);

		when(proposerElection.getProposer(any())).thenReturn(ECKeyPair.generateNew().getPublicKey());

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
		when(proposerElection.getProposer(any())).thenReturn(ECKeyPair.generateNew().getPublicKey());

		eventCoordinator.processProposal(proposalVertex);
		verify(mempool, times(1)).removeCommittedAtom(eq(aid));
	}
}
