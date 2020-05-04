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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.SyncQueues.SyncQueue;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
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
	private SyncQueues syncQueues;

	@Before
	public void setUp() {
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStore = mock(VertexStore.class);
		this.proposerElection = mock(ProposerElection.class);
		this.counters = mock(SystemCounters.class);
		this.forwardTo = mock(BFTEventProcessor.class);
		this.syncQueues = mock(SyncQueues.class);

		when(proposerElection.getProposer(any())).thenReturn(SELF_KEY.getPublicKey());
		when(pacemaker.getCurrentView()).thenReturn(View.of(1));

		this.preprocessor = new BFTEventPreprocessor(
			SELF_KEY.getPublicKey(),
			forwardTo,
			pacemaker,
			vertexStore,
			proposerElection,
			syncQueues,
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
		Hash vertexId = mock(Hash.class);
		when(vertex.getId()).thenReturn(vertexId);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		VertexMetadata proposed = mock(VertexMetadata.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(vertex.getQC()).thenReturn(qc);
		when(vertexStore.syncToQC(eq(qc))).thenReturn(synced);
		return proposal;
	}


	@Test
	public void when_process_vote_as_not_proposer__then_vote_gets_thrown_away() {
		Vote vote = mock(Vote.class);
		VoteData voteData = mock(VoteData.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).thenReturn(View.of(1));
		when(voteData.getProposed()).thenReturn(vertexMetadata);
		when(vote.getVoteData()).thenReturn(voteData);
		when(proposerElection.getProposer(eq(View.of(1)))).thenReturn(mock(ECPublicKey.class));
		preprocessor.processVote(vote);
		verify(forwardTo, never()).processVote(vote);
	}

	@Test
	public void when_process_vote__event_gets_forwarded() {
		Vote vote = mock(Vote.class);
		VoteData voteData = mock(VoteData.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).thenReturn(View.of(1));
		when(voteData.getProposed()).thenReturn(vertexMetadata);
		when(vote.getVoteData()).thenReturn(voteData);
		preprocessor.processVote(vote);
		verify(forwardTo, times(1)).processVote(vote);
	}

	@Test
	public void when_process_irrelevant_new_view__event_gets_thrown_away() {
		NewView newView = createNewView(false, true);
		when(syncQueues.checkOrAdd(any())).thenReturn(true);
		preprocessor.processNewView(newView);
		verify(syncQueues, never()).add(any());
		verify(forwardTo, never()).processNewView(any());
	}

	@Test
	public void when_process_irrelevant_proposal__event_gets_thrown_away() {
		Proposal proposal = createProposal(false, true);
		when(syncQueues.checkOrAdd(any())).thenReturn(true);
		preprocessor.processProposal(proposal);
		verify(syncQueues, never()).add(any());
		verify(forwardTo, never()).processProposal(any());
	}

	@Test
	public void when_processing_new_view_as_not_proposer__then_new_view_get_thrown_away() {
		NewView newView = createNewView(true, true);
		when(syncQueues.checkOrAdd(eq(newView))).thenReturn(true);
		when(proposerElection.getProposer(View.of(2))).thenReturn(mock(ECPublicKey.class));
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		preprocessor.processNewView(newView);
		verify(forwardTo, never()).processNewView(any());
	}

	@Test
	public void when_process_new_view_not_synced__then_new_view_is_queued() {
		NewView newView = createNewView(true, false);
		when(syncQueues.checkOrAdd(eq(newView))).thenReturn(true);
		preprocessor.processNewView(newView);
		verify(syncQueues, times(1)).add(eq(newView));
		verify(forwardTo, never()).processNewView(any());
	}

	@Test
	public void when_process_proposal_not_synced__then_proposal_is_queued() {
		Proposal proposal = createProposal(true, false);
		when(syncQueues.checkOrAdd(eq(proposal))).thenReturn(true);
		preprocessor.processProposal(proposal);
		verify(syncQueues, times(1)).add(eq(proposal));
		verify(forwardTo, never()).processProposal(any());
	}

	@Test
	public void when_process_new_view_synced__then_new_view_is_forwarded() {
		NewView newView = createNewView(true, true);
		when(syncQueues.checkOrAdd(eq(newView))).thenReturn(true);
		preprocessor.processNewView(newView);
		verify(syncQueues, never()).add(any());
		verify(forwardTo, times(1)).processNewView(eq(newView));
	}

	@Test
	public void when_process_proposal_synced__then_proposal_is_forwarded() {
		Proposal proposal = createProposal(true, true);
		when(syncQueues.checkOrAdd(eq(proposal))).thenReturn(true);
		preprocessor.processProposal(proposal);
		verify(syncQueues, never()).add(any());
		verify(forwardTo, times(1)).processProposal(eq(proposal));
	}

	@Test
	public void when_proposal_with_store_with_equal_hash_and_no_issues__then_remove_from_queue_and_forward() {
		Proposal proposal = createProposal(true, true);
		Vertex vertex = mock(Vertex.class);
		Hash vertexId = mock(Hash.class);
		when(vertex.getId()).thenReturn(vertexId);
		when(vertex.getView()).thenReturn(View.of(1));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);
		when(vertexStore.syncToQC(qc)).thenReturn(true);
		when(proposal.getVertex()).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(OTHER_KEY.getPublicKey());
		when(vertexStore.getVertex(eq(vertexId))).thenReturn(vertex);
		when(syncQueues.checkOrAdd(eq(proposal))).thenReturn(true);
		SyncQueue syncQueue = mock(SyncQueue.class);
		NewView newView = createNewView(true, true);
		when(syncQueue.peek(eq(vertexId))).thenReturn(newView);
		when(syncQueue.peek(isNull())).thenReturn(null);
		ImmutableCollection<SyncQueue> queues = ImmutableSet.of(syncQueue);
		when(syncQueues.getQueues()).thenReturn(queues);

		preprocessor.processProposal(proposal);
		verify(forwardTo, times(1)).processProposal(eq(proposal));
		verify(forwardTo, times(1)).processNewView(eq(newView));
		verify(syncQueue, times(1)).peek(eq(vertexId));
		verify(syncQueue, times(1)).peek(isNull());
		verify(syncQueue, times(1)).pop();
	}

	@Test
	public void when_proposal_with_store_with_unequal_hash__then_queue_remains_the_same() {
		Proposal proposal = createProposal(true, true);
		Vertex vertex = mock(Vertex.class);
		Hash vertexId = mock(Hash.class);
		when(vertex.getId()).thenReturn(vertexId);
		when(vertex.getView()).thenReturn(View.of(1));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);
		when(vertexStore.syncToQC(qc)).thenReturn(true);
		when(proposal.getVertex()).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(OTHER_KEY.getPublicKey());
		when(vertexStore.getVertex(eq(vertexId))).thenReturn(vertex);
		when(syncQueues.checkOrAdd(eq(proposal))).thenReturn(true);
		SyncQueue syncQueue = mock(SyncQueue.class);
		NewView newView = createNewView(true, true);
		when(syncQueue.peek(eq(vertexId))).thenReturn(null);
		when(syncQueue.peek(isNull())).thenReturn(null);
		ImmutableCollection<SyncQueue> queues = ImmutableSet.of(syncQueue);
		when(syncQueues.getQueues()).thenReturn(queues);

		preprocessor.processProposal(proposal);
		verify(forwardTo, times(1)).processProposal(eq(proposal));
		verify(forwardTo, times(0)).processNewView(eq(newView));
		verify(syncQueue, times(1)).peek(eq(vertexId));
		verify(syncQueue, times(0)).peek(isNull());
		verify(syncQueue, times(0)).pop();
	}


	@Test
	public void when_proposal_with_store_with_equal_hash_and_sync_issues__then_keep_in_queue() {
		Proposal proposal = createProposal(true, true);
		Vertex vertex = mock(Vertex.class);
		Hash vertexId = mock(Hash.class);
		when(vertex.getId()).thenReturn(vertexId);
		when(vertex.getView()).thenReturn(View.of(1));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vertex.getQC()).thenReturn(qc);
		when(vertexStore.syncToQC(qc)).thenReturn(true);
		when(proposal.getVertex()).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(OTHER_KEY.getPublicKey());
		when(vertexStore.getVertex(eq(vertexId))).thenReturn(vertex);
		when(syncQueues.checkOrAdd(eq(proposal))).thenReturn(true);
		SyncQueue syncQueue = mock(SyncQueue.class);

		NewView newView = createNewView(true, false);
		when(syncQueue.peek(eq(vertexId))).thenReturn(newView);
		ImmutableCollection<SyncQueue> queues = ImmutableSet.of(syncQueue);
		when(syncQueues.getQueues()).thenReturn(queues);

		preprocessor.processProposal(proposal);
		verify(forwardTo, times(1)).processProposal(eq(proposal));
		verify(forwardTo, times(0)).processNewView(eq(newView));
		verify(syncQueue, times(1)).peek(eq(vertexId));
		verify(syncQueue, times(0)).peek(isNull());
		verify(syncQueue, times(0)).pop();
	}
}