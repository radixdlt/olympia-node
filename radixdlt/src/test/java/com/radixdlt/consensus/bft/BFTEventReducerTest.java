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

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTInfoSender;
import com.radixdlt.consensus.bft.BFTEventReducer.EndOfEpochSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.identifiers.AID;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.utils.Ints;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BFTEventReducerTest {
	private BFTEventReducer reducer;
	private NextCommandGenerator nextCommandGenerator;
	private ProposerElection proposerElection;
	private SafetyRules safetyRules;
	private Pacemaker pacemaker;
	private PendingVotes pendingVotes;
	private BFTEventReducer.BFTEventSender sender;
	private EndOfEpochSender endOfEpochSender;
	private VertexStore vertexStore;
	private BFTValidatorSet validatorSet;
	private SystemCounters counters;
	private BFTInfoSender infoSender;
	private BFTNode self;

	@Before
	public void setUp() {
		this.nextCommandGenerator = mock(NextCommandGenerator.class);
		this.sender = mock(BFTEventReducer.BFTEventSender.class);
		this.endOfEpochSender = mock(EndOfEpochSender.class);
		this.safetyRules = mock(SafetyRules.class);
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStore = mock(VertexStore.class);
		this.pendingVotes = mock(PendingVotes.class);
		this.proposerElection = mock(ProposerElection.class);
		this.validatorSet = mock(BFTValidatorSet.class);
		this.counters = mock(SystemCounters.class);
		this.infoSender = mock(BFTInfoSender.class);
		this.self = mock(BFTNode.class);

		this.reducer = new BFTEventReducer(
			self, nextCommandGenerator,
			sender,
			endOfEpochSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			validatorSet,
			counters,
			infoSender,
			System::currentTimeMillis
		);
	}

	private static AID makeAID(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return AID.from(temp);
	}

	@Test
	public void when_start__then_should_proceed_to_first_view() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		when(proposerElection.getProposer(any())).thenReturn(self);
		when(vertexStore.getHighestQC()).thenReturn(qc);
		when(pacemaker.processQC(eq(view))).thenReturn(Optional.of(mock(View.class)));
		reducer.start();
		verify(pacemaker, times(1)).processQC(eq(view));
		verify(sender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_local_sync__then_should_process_it_via_vertex_store() {
		Hash vertexId = mock(Hash.class);
		reducer.processLocalSync(vertexId);
		verify(vertexStore, times(1)).processLocalSync(eq(vertexId));
	}

	@Test
	public void when_process_vote_and_new_qc_not_synced__then_local_sync_should_cause_it_to_process_it() {
		Vote vote = mock(Vote.class);
		when(vote.getAuthor()).thenReturn(mock(BFTNode.class));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		Hash id = mock(Hash.class);
		when(vertexMetadata.getId()).thenReturn(id);
		when(qc.getProposed()).thenReturn(vertexMetadata);
		when(pendingVotes.insertVote(eq(vote), eq(validatorSet))).thenReturn(Optional.of(qc));
		when(vertexStore.syncToQC(eq(qc), any(), any())).thenReturn(false);
		reducer.processVote(vote);
		verify(safetyRules, never()).process(any());
		verify(pacemaker, never()).processQC(any());

		when(safetyRules.process(any())).thenReturn(Optional.empty());
		when(pacemaker.processQC(any())).thenReturn(Optional.empty());
		reducer.processLocalSync(id);
		verify(safetyRules, never()).process(eq(qc));
		verify(pacemaker, never()).processQC(eq(view));
	}

	@Test
	public void when_processing_vote_as_not_proposer__then_nothing_happens() {
		Vote voteMessage = mock(Vote.class);
		VertexMetadata proposal = new VertexMetadata(0, View.of(2), Hash.random(), 2, false);
		VertexMetadata parent = new VertexMetadata(0, View.of(1), Hash.random(), 1, false);
		VoteData voteData = new VoteData(proposal, parent, null);
		when(voteMessage.getVoteData()).thenReturn(voteData);

		reducer.processVote(voteMessage);
		verify(safetyRules, times(0)).process(any(QuorumCertificate.class));
		verify(pacemaker, times(0)).processQC(any());
	}

	@Test
	public void when_processing_vote_as_a_proposer_and_quorum_is_reached__then_a_new_view_is_sent() {
		when(proposerElection.getProposer(any())).thenReturn(this.self);

		Vote vote = mock(Vote.class);
		VertexMetadata proposal = new VertexMetadata(0, View.of(2), Hash.random(), 2, false);
		VertexMetadata parent = new VertexMetadata(0, View.of(1), Hash.random(), 1, false);
		VoteData voteData = new VoteData(proposal, parent, null);
		when(vote.getVoteData()).thenReturn(voteData);
		when(vote.getAuthor()).thenReturn(mock(BFTNode.class));

		QuorumCertificate qc = mock(QuorumCertificate.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		when(pendingVotes.insertVote(eq(vote), any())).thenReturn(Optional.of(qc));
		when(pacemaker.getCurrentView()).thenReturn(mock(View.class));
		when(pacemaker.processQC(eq(view))).thenReturn(Optional.of(mock(View.class)));
		when(vertexStore.syncToQC(eq(qc), any(), any())).thenReturn(true);
		when(vertexStore.getHighestQC()).thenReturn(mock(QuorumCertificate.class));

		reducer.processVote(vote);

		verify(sender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_relevant_local_timeout__then_new_view_is_emitted_and_counter_increment() {
        when(proposerElection.getProposer(any())).thenReturn(mock(BFTNode.class));
		when(pacemaker.processLocalTimeout(any())).thenReturn(Optional.of(View.of(1)));
		when(pacemaker.getCurrentView()).thenReturn(View.of(1));
		when(vertexStore.getHighestQC()).thenReturn(mock(QuorumCertificate.class));
		reducer.processLocalTimeout(View.of(0L));
		verify(sender, times(1)).sendNewView(any(), any());
		verify(counters, times(1)).increment(eq(CounterType.BFT_TIMEOUT));
	}

	@Test
	public void when_processing_irrelevant_local_timeout__then_new_view_is_not_emitted_and_no_counter_increment() {
		when(pacemaker.processLocalTimeout(any())).thenReturn(Optional.empty());
		reducer.processLocalTimeout(View.of(0L));
		verify(sender, times(0)).sendNewView(any(), any());
		verify(counters, times(0)).increment(eq(CounterType.BFT_TIMEOUT));
	}


	@Test
	public void when_processing_new_view_as_proposer__then_new_view_is_processed_and_proposal_is_sent() {
		NewView newView = mock(NewView.class);
		when(newView.getQC()).thenReturn(mock(QuorumCertificate.class));
		when(newView.getView()).thenReturn(View.of(0L));
		when(pacemaker.getCurrentView()).thenReturn(View.of(0L));
		when(pacemaker.processNewView(any(), any())).thenReturn(Optional.of(View.of(1L)));
		when(proposerElection.getProposer(any())).thenReturn(self);
		QuorumCertificate highQC = mock(QuorumCertificate.class);
		when(highQC.getProposed()).thenReturn(mock(VertexMetadata.class));
		when(vertexStore.getHighestQC()).thenReturn(highQC);
		when(nextCommandGenerator.generateNextCommand(eq(View.of(1L)), any())).thenReturn(mock(ClientAtom.class));
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of());
		reducer.processNewView(newView);
		verify(pacemaker, times(1)).processNewView(any(), any());
		verify(sender, times(1)).broadcastProposal(any(), any());
	}

	@Test
	public void when_processing_invalid_proposal__then_atom_is_rejected() throws Exception {
		View currentView = View.of(123);

		Vertex proposedVertex = mock(Vertex.class);
		ClientAtom proposedAtom = mock(ClientAtom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		when(proposedVertex.getQC()).thenReturn(mock(QuorumCertificate.class));
		VertexMetadata parent = mock(VertexMetadata.class);
		when(proposedVertex.getParentMetadata()).thenReturn(parent);
		when(proposedVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		RadixEngineException e = mock(RadixEngineException.class);
		Mockito.doThrow(new VertexInsertionException("Test", e))
			.when(vertexStore).insertVertex(any());
		when(pacemaker.processQC(any())).thenReturn(Optional.empty());
		when(pacemaker.getCurrentView()).thenReturn(currentView);
		reducer.processProposal(proposal);
	}

	@Test
	public void when_processing_valid_stored_proposal__then_atom_is_voted_on_and_new_view() throws SafetyViolationException {
		View currentView = View.of(123);

		when(proposerElection.getProposer(any())).thenReturn(mock(BFTNode.class));

		Vertex proposedVertex = mock(Vertex.class);
		ClientAtom proposedAtom = mock(ClientAtom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);
		VertexMetadata parent = mock(VertexMetadata.class);
		when(proposedVertex.getParentMetadata()).thenReturn(parent);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(eq(proposedVertex), any(), anyLong(), anyLong());
		when(pacemaker.processQC(eq(qcView))).thenReturn(Optional.empty());
		when(pacemaker.processQC(eq(currentView))).thenReturn(Optional.of(View.of(124)));
		when(vertexStore.getHighestQC()).thenReturn(mock(QuorumCertificate.class));

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(sender, times(1)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_valid_stored_proposal_and_next_leader__then_atom_is_voted_on_and_new_view() throws SafetyViolationException {
		View currentView = View.of(123);

		when(proposerElection.getProposer(eq(currentView))).thenReturn(mock(BFTNode.class));
		when(proposerElection.getProposer(eq(currentView.next()))).thenReturn(self);

		Vertex proposedVertex = mock(Vertex.class);
		ClientAtom proposedAtom = mock(ClientAtom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);
		VertexMetadata parent = mock(VertexMetadata.class);
		when(proposedVertex.getParentMetadata()).thenReturn(parent);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(eq(proposedVertex), any(), anyLong(), anyLong());
		when(pacemaker.processQC(eq(qcView))).thenReturn(Optional.empty());
		when(pacemaker.processQC(eq(currentView))).thenReturn(Optional.of(View.of(124)));

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(sender, times(0)).sendNewView(any(), any());
	}

	@Test
	public void when_processing_valid_stored_proposal_and_leader__then_atom_is_voted_on_and_no_new_view() throws SafetyViolationException {
		View currentView = View.of(123);

		when(proposerElection.getProposer(eq(currentView))).thenReturn(self);

		Vertex proposedVertex = mock(Vertex.class);
		ClientAtom proposedAtom = mock(ClientAtom.class);
		AID aid = makeAID(7); // no special significance
		when(proposedAtom.getAID()).thenReturn(aid);
		when(proposedVertex.getAtom()).thenReturn(proposedAtom);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);
		VertexMetadata parent = mock(VertexMetadata.class);
		when(proposedVertex.getParentMetadata()).thenReturn(parent);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(eq(proposedVertex), any(), anyLong(), anyLong());
		when(pacemaker.processQC(eq(qcView))).thenReturn(Optional.empty());
		when(pacemaker.processQC(eq(currentView))).thenReturn(Optional.of(View.of(124)));

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(sender, times(0)).sendNewView(any(), any());
	}


	@Test
	public void when_processing_valid_stored_proposal_and_there_exists_a_new_commit__the_new_commit_atom_is_removed_from_mempool() {
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
		VertexMetadata parent = mock(VertexMetadata.class);
		when(proposalVertex.getParentMetadata()).thenReturn(parent);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposalVertex);

		VertexMetadata committedVertexMetadata = mock(VertexMetadata.class);
		Hash committedVertexId = mock(Hash.class);
		when(committedVertexMetadata.getId()).thenReturn(committedVertexId);
		Vertex committedVertex = mock(Vertex.class);
		ClientAtom atom = mock(ClientAtom.class);
		AID aid = mock(AID.class);
		when(atom.getAID()).thenReturn(aid);
		when(committedVertex.getAtom()).thenReturn(atom);

		when(safetyRules.process(eq(qc))).thenReturn(Optional.of(committedVertexMetadata));
		when(vertexStore.commitVertex(eq(committedVertexMetadata))).thenReturn(Optional.of(committedVertex));
		when(proposerElection.getProposer(any())).thenReturn(mock(BFTNode.class));

		reducer.processProposal(proposal);
	}
}
