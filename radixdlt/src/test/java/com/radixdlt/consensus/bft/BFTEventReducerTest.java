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

import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.crypto.Hash;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class BFTEventReducerTest {
	private BFTEventReducer reducer;
	private Pacemaker pacemaker;
	private VertexStore vertexStore;
	private BFTSync vertexStoreSync;

	@Before
	public void setUp() {
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStore = mock(VertexStore.class);
		this.vertexStoreSync = mock(BFTSync.class);

		this.reducer = new BFTEventReducer(
			pacemaker,
			vertexStore,
			vertexStoreSync
		);
	}

	@Test
	public void when_start__then_should_proceed_to_first_view() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		HighQC syncInfo = mock(HighQC.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		when(syncInfo.highestQC()).thenReturn(qc);
		when(vertexStore.syncInfo()).thenReturn(syncInfo);
		reducer.start();
		verify(pacemaker, times(1)).processQC(syncInfo);
		verifyNoMoreInteractions(pacemaker);
	}

	@Test
	public void when_processing_vote_no_quorum__then_pacemaker_processes() {
		Vote voteMessage = mock(Vote.class);
		BFTHeader proposal = new BFTHeader(View.of(2), Hash.random(), mock(LedgerHeader.class));
		BFTHeader parent = new BFTHeader(View.of(1), Hash.random(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(proposal, parent, null);
		when(voteMessage.getVoteData()).thenReturn(voteData);

		reducer.processVote(voteMessage);
		verify(pacemaker, times(1)).processVote(eq(voteMessage));
		verifyNoMoreInteractions(pacemaker);
	}

	@Test
	public void when_processing_vote_with_quorum_and_synced__then_pacemaker_processes() {
		Vote voteMessage = mock(Vote.class);
		BFTHeader proposal = new BFTHeader(View.of(2), Hash.random(), mock(LedgerHeader.class));
		BFTHeader parent = new BFTHeader(View.of(1), Hash.random(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(proposal, parent, null);
		when(voteMessage.getVoteData()).thenReturn(voteData);

		HighQC syncInfo = mock(HighQC.class);
		when(syncInfo.highestCommittedQC()).thenReturn(mock(QuorumCertificate.class));

		when(pacemaker.processVote(any())).thenReturn(Optional.of(mock(QuorumCertificate.class)));
		when(vertexStore.syncInfo()).thenReturn(syncInfo);
		when(vertexStoreSync.syncToQC(any(), any())).thenReturn(SyncResult.SYNCED);

		reducer.processVote(voteMessage);

		verify(pacemaker, times(1)).processVote(eq(voteMessage));
		verify(vertexStoreSync, times(1)).syncToQC(any(),  any());
		verifyNoMoreInteractions(pacemaker);
	}

	@Test
	public void when_processing_vote_with_quorum_and_not_synced__then_pacemaker_processes() {
		Vote voteMessage = mock(Vote.class);
		BFTHeader proposal = new BFTHeader(View.of(2), Hash.random(), mock(LedgerHeader.class));
		BFTHeader parent = new BFTHeader(View.of(1), Hash.random(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(proposal, parent, null);
		when(voteMessage.getVoteData()).thenReturn(voteData);

		HighQC syncInfo = mock(HighQC.class);
		when(syncInfo.highestCommittedQC()).thenReturn(mock(QuorumCertificate.class));

		when(pacemaker.processVote(eq(voteMessage))).thenReturn(Optional.of(mock(QuorumCertificate.class)));
		when(vertexStore.syncInfo()).thenReturn(syncInfo);
		when(vertexStoreSync.syncToQC(any(), any())).thenReturn(SyncResult.IN_PROGRESS);

		reducer.processVote(voteMessage);

		verify(pacemaker, times(1)).processVote(eq(voteMessage));
		verifyNoMoreInteractions(pacemaker);
	}

	@Test
	public void when_processing_relevant_local_timeout__then_pacemaker_processes() {
		reducer.processLocalTimeout(View.of(0L));

		verify(pacemaker, times(1)).processLocalTimeout(eq(View.of(0L)));
		verifyNoMoreInteractions(pacemaker);
	}

	@Test
	public void when_processing_view_timeout__then_pacemaker_processes() {
		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		reducer.processViewTimeout(viewTimeout);

		verify(pacemaker, times(1)).processViewTimeout(eq(viewTimeout));
		verifyNoMoreInteractions(pacemaker);
	}

	@Test
	public void when_processing_proposal__then_pacemaker_processes() {
		Proposal proposal = mock(Proposal.class);
		reducer.processProposal(proposal);

		verify(pacemaker, times(1)).processProposal(eq(proposal));
		verifyNoMoreInteractions(pacemaker);
	}

	@Test
	public void when_processing_update__then_pacemaker_does_not_processes() {
		BFTUpdate update = mock(BFTUpdate.class);
		reducer.processBFTUpdate(update);

		verifyNoMoreInteractions(pacemaker);
	}
}
