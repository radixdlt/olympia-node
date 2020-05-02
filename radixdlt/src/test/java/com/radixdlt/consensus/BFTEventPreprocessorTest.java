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

package com.radixdlt.consensus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class BFTEventPreprocessorTest {
	private static final ECKeyPair SELF_KEY = ECKeyPair.generateNew();
	private static final ECKeyPair OTHER_KEY = ECKeyPair.generateNew();
	private BFTEventPreprocessor preprocessor;
	private ProposerElection proposerElection;
	private Pacemaker pacemaker;
	private VertexStore vertexStore;
	private SystemCounters counters;
	private BFTEventProcessor forwardTo;
	private HashMap<ECPublicKey, List<ConsensusEvent>> initialQueues;

	@Before
	public void setUp() {
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStore = mock(VertexStore.class);
		this.proposerElection = mock(ProposerElection.class);
		this.counters = mock(SystemCounters.class);
		this.forwardTo = mock(BFTEventProcessor.class);
		this.initialQueues = new HashMap<>();
		this.initialQueues.put(SELF_KEY.getPublicKey(), Collections.emptyList());
		this.initialQueues.put(OTHER_KEY.getPublicKey(), Collections.emptyList());

		when(proposerElection.getProposer(any())).thenReturn(SELF_KEY.getPublicKey());
		when(pacemaker.getCurrentView()).thenReturn(View.of(1));

		this.preprocessor = new BFTEventPreprocessor(
			SELF_KEY.getPublicKey(),
			forwardTo,
			pacemaker,
			vertexStore,
			proposerElection,
			initialQueues,
			counters
		);
	}

	private void setInitialQueue(ECPublicKey key, ConsensusEvent... events) {
		initialQueues.put(key, Arrays.asList(events));
		this.preprocessor = new BFTEventPreprocessor(
			SELF_KEY.getPublicKey(),
			forwardTo,
			pacemaker,
			vertexStore,
			proposerElection,
			initialQueues,
			counters
		);
	}

	private NewView createNewView(boolean goodView, boolean synced) {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView.getView()).thenReturn(goodView ? View.of(2) : View.of(0));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		Hash vertexId = mock(Hash.class);
		VertexMetadata proposed = mock(VertexMetadata.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(proposed.getId()).thenReturn(vertexId);
		when(newView.getQC()).thenReturn(qc);
		when(vertexStore.syncToQC(eq(qc))).thenReturn(synced);
		return newView;
	}

	private Proposal createProposal(boolean goodView, boolean synced) {
		Proposal proposal = mock(Proposal.class);
		when(proposal.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		Vertex vertex = mock(Vertex.class);
		when(proposal.getVertex()).thenReturn(vertex);
		when(vertex.getView()).thenReturn(goodView ? View.of(1) : View.of(0));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		Hash vertexId = mock(Hash.class);
		when(vertex.getId()).thenReturn(vertexId);
		VertexMetadata proposed = mock(VertexMetadata.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(proposed.getId()).thenReturn(vertexId);
		when(vertex.getQC()).thenReturn(qc);
		when(vertexStore.syncToQC(eq(qc))).thenReturn(synced);
		return proposal;
	}

	@Test
	public void when_process_irrelevant_new_view__event_gets_thrown_away() {
		NewView newView = createNewView(false, true);
		preprocessor.processNewView(newView);
		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).isEmpty();
		verify(forwardTo, never()).processNewView(any());
	}

	@Test
	public void when_process_irrelevant_proposal__event_gets_thrown_away() {
		Proposal proposal = createProposal(false, true);
		preprocessor.processProposal(proposal);
		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).isEmpty();
		verify(forwardTo, never()).processProposal(any());
	}

	@Test
	public void when_process_new_view_not_synced__then_new_view_is_queued() {
		NewView newView = createNewView(true, false);
		preprocessor.processNewView(newView);
		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).containsExactly(newView);
		verify(forwardTo, never()).processNewView(any());
	}

	@Test
	public void when_process_proposal_not_synced__then_proposal_is_queued() {
		Proposal proposal = createProposal(true, false);
		preprocessor.processProposal(proposal);
		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).containsExactly(proposal);
		verify(forwardTo, never()).processProposal(any());
	}

	@Test
	public void when_process_new_view_synced__then_new_view_is_forwarded() {
		NewView newView = createNewView(true, true);
		preprocessor.processNewView(newView);
		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).isEmpty();
		verify(forwardTo, times(1)).processNewView(eq(newView));
	}

	@Test
	public void when_process_proposal_synced__then_proposal_is_forwarded() {
		Proposal proposal = createProposal(true, true);
		preprocessor.processProposal(proposal);
		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).isEmpty();
		verify(forwardTo, times(1)).processProposal(eq(proposal));
	}

	@Test
	public void when_initial_queue_and_a_proposal_store_with_unequal_hash__then_queue_remains_the_same() {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView.getView()).thenReturn(View.of(2));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		Hash vertexId = mock(Hash.class);
		VertexMetadata proposed = mock(VertexMetadata.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(proposed.getId()).thenReturn(vertexId);
		when(newView.getQC()).thenReturn(qc);

		setInitialQueue(SELF_KEY.getPublicKey(), newView);

		Proposal proposal = mock(Proposal.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		QuorumCertificate otherQc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(otherQc);
		Hash otherId = mock(Hash.class);
		when(vertex.getId()).thenReturn(otherId);
		when(proposal.getVertex()).thenReturn(vertex);
		when(vertexStore.syncToQC(eq(otherQc))).thenReturn(true);
		when(vertexStore.getVertex(eq(otherId))).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(OTHER_KEY.getPublicKey());
		preprocessor.processProposal(proposal);
		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).containsExactly(newView);
	}

	@Test
	public void when_initial_queue_and_a_proposal_store_with_equal_hash__then_queue_becomes_empty() {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView.getView()).thenReturn(View.of(2));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		Hash vertexId = mock(Hash.class);
		VertexMetadata proposed = mock(VertexMetadata.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(proposed.getId()).thenReturn(vertexId);
		when(newView.getQC()).thenReturn(qc);

		setInitialQueue(SELF_KEY.getPublicKey(), newView);

		Proposal proposal = mock(Proposal.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		QuorumCertificate otherQc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(otherQc);
		when(vertex.getId()).thenReturn(vertexId);
		when(proposal.getVertex()).thenReturn(vertex);
		when(vertexStore.syncToQC(eq(otherQc))).thenReturn(true);
		when(vertexStore.syncToQC(eq(qc))).thenReturn(true);
		when(vertexStore.getVertex(eq(vertexId))).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(OTHER_KEY.getPublicKey());
		preprocessor.processProposal(proposal);
		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).isEmpty();
	}

	@Test
	public void when_initial_queue_with_two_and_a_proposal_store_with_equal_hash__then_queue_becomes_empty() {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView.getView()).thenReturn(View.of(2));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		Hash vertexId = mock(Hash.class);
		VertexMetadata proposed = mock(VertexMetadata.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(proposed.getId()).thenReturn(vertexId);
		when(newView.getQC()).thenReturn(qc);

		NewView newView2 = mock(NewView.class);
		when(newView2.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView2.getView()).thenReturn(View.of(2));
		QuorumCertificate qc2 = mock(QuorumCertificate.class);
		Hash vertexId2 = mock(Hash.class);
		VertexMetadata proposed2 = mock(VertexMetadata.class);
		when(qc2.getProposed()).thenReturn(proposed2);
		when(proposed2.getId()).thenReturn(vertexId2);
		when(newView2.getQC()).thenReturn(qc2);

		setInitialQueue(SELF_KEY.getPublicKey(), newView, newView2);

		Proposal proposal = mock(Proposal.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		QuorumCertificate otherQc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(otherQc);
		when(vertex.getId()).thenReturn(vertexId);
		when(proposal.getVertex()).thenReturn(vertex);
		when(vertexStore.syncToQC(eq(otherQc))).thenReturn(true);
		when(vertexStore.syncToQC(eq(qc))).thenReturn(true);
		when(vertexStore.syncToQC(eq(qc2))).thenReturn(true);
		when(vertexStore.getVertex(eq(vertexId))).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(OTHER_KEY.getPublicKey());
		preprocessor.processProposal(proposal);

		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).isEmpty();
	}

	@Test
	public void when_initial_queue_with_two_and_a_proposal_store_with_equal_hash_but_next_fails_sync__then_queue_contains_one() {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView.getView()).thenReturn(View.of(2));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		Hash vertexId = mock(Hash.class);
		VertexMetadata proposed = mock(VertexMetadata.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(proposed.getId()).thenReturn(vertexId);
		when(newView.getQC()).thenReturn(qc);

		NewView newView2 = mock(NewView.class);
		when(newView2.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView2.getView()).thenReturn(View.of(2));
		QuorumCertificate qc2 = mock(QuorumCertificate.class);
		Hash vertexId2 = mock(Hash.class);
		VertexMetadata proposed2 = mock(VertexMetadata.class);
		when(qc2.getProposed()).thenReturn(proposed2);
		when(proposed2.getId()).thenReturn(vertexId2);
		when(newView2.getQC()).thenReturn(qc2);

		setInitialQueue(SELF_KEY.getPublicKey(), newView, newView2);

		Proposal proposal = mock(Proposal.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		QuorumCertificate otherQc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(otherQc);
		when(vertex.getId()).thenReturn(vertexId);
		when(proposal.getVertex()).thenReturn(vertex);
		when(vertexStore.syncToQC(eq(otherQc))).thenReturn(true);
		when(vertexStore.syncToQC(eq(qc))).thenReturn(true);
		when(vertexStore.syncToQC(eq(qc2))).thenReturn(false);
		when(vertexStore.getVertex(eq(vertexId))).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(OTHER_KEY.getPublicKey());
		preprocessor.processProposal(proposal);

		assertThat(preprocessor.getQueues().get(SELF_KEY.getPublicKey())).containsExactly(newView2);
	}

	@Test
	public void when_processing_old_proposal__then_no_vertex_is_inserted() {
		when(pacemaker.getCurrentView()).thenReturn(View.of(10));
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(9));
		Proposal proposal = mock(Proposal.class);
		when(proposal.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(proposal.getVertex()).thenReturn(vertex);
		preprocessor.processProposal(proposal);
		verify(forwardTo, never()).processProposal(any());
	}

	@Test
	public void when_processing_new_view_as_not_proposer__then_new_view_is_not_emitted() {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView.getView()).thenReturn(View.of(0L));
		when(pacemaker.getCurrentView()).thenReturn(View.of(0L));
		preprocessor.processNewView(newView);
		verify(forwardTo, never()).processNewView(any());
	}
}