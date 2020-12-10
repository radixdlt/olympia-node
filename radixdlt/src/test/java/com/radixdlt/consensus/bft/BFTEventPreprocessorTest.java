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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.utils.Pair;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class BFTEventPreprocessorTest {

    private BFTEventPreprocessor preprocessor;
    private BFTSync vertexStoreSync;
    private BFTEventProcessor forwardTo;
    private SyncQueues syncQueues;

    @Before
    public void setUp() {
        this.vertexStoreSync = mock(BFTSync.class);
        this.forwardTo = mock(BFTEventProcessor.class);
        this.syncQueues = mock(SyncQueues.class);

        when(syncQueues.isEmptyElseAdd(any())).thenReturn(true);

        this.preprocessor = new BFTEventPreprocessor(
            forwardTo,
            vertexStoreSync,
            syncQueues,
            ViewUpdate.create(View.genesis().next(), mock(HighQC.class), mock(BFTNode.class), BFTNode.random())
        );
    }

    private Proposal createProposal(boolean goodView, boolean synced) {
        Proposal proposal = mock(Proposal.class);
        UnverifiedVertex vertex = mock(UnverifiedVertex.class);
        when(proposal.getVertex()).thenReturn(vertex);
        when(vertex.getView()).thenReturn(goodView ? View.of(1) : View.of(0));
        when(proposal.getView()).thenReturn(goodView ? View.of(1) : View.of(0));

        QuorumCertificate qc = mock(QuorumCertificate.class);
        BFTHeader proposed = mock(BFTHeader.class);
        when(qc.getProposed()).thenReturn(proposed);
        when(vertex.getQC()).thenReturn(qc);

        QuorumCertificate committedQC = mock(QuorumCertificate.class);
        when(committedQC.getCommittedAndLedgerStateProof()).thenReturn(
                Optional.of(Pair.of(mock(BFTHeader.class), mock(VerifiedLedgerHeaderAndProof.class)))
        );
        HighQC highQC = mock(HighQC.class);
        when(highQC.highestQC()).thenReturn(qc);
        when(highQC.highestCommittedQC()).thenReturn(committedQC);
        when(proposal.highQC()).thenReturn(highQC);

        when(vertexStoreSync.syncToQC(any(), any())).thenReturn(synced ? SyncResult.SYNCED : SyncResult.IN_PROGRESS);
        return proposal;
    }

    @Test
    public void when_process_vote_unsynced__event_not_forwarded() {
        Vote vote = mock(Vote.class);
        when(vote.getView()).thenReturn(View.of(1));
        when(vote.getSignature()).thenReturn(mock(ECDSASignature.class));
        when(vote.getAuthor()).thenReturn(mock(BFTNode.class));
        when(vertexStoreSync.syncToQC(any(), any())).thenReturn(SyncResult.IN_PROGRESS);
        preprocessor.processVote(vote);
        verify(forwardTo, never()).processVote(vote);
    }

    @Test
    public void when_process_irrelevant_proposal__event_gets_thrown_away() {
        Proposal proposal = createProposal(false, true);
        when(syncQueues.isEmptyElseAdd(any())).thenReturn(true);
        preprocessor.processProposal(proposal);
        verify(syncQueues, never()).add(any());
        verify(forwardTo, never()).processProposal(any());
    }

    @Test
    public void when_process_proposal_not_synced__then_proposal_is_queued() {
        Proposal proposal = createProposal(true, false);
        when(syncQueues.isEmptyElseAdd(eq(proposal))).thenReturn(true);
        preprocessor.processProposal(proposal);
        verify(syncQueues, times(1)).add(eq(proposal));
        verify(forwardTo, never()).processProposal(any());
    }

    @Test
    public void when_process_proposal_synced__then_proposal_is_forwarded() {
        Proposal proposal = createProposal(true, true);
        when(syncQueues.isEmptyElseAdd(eq(proposal))).thenReturn(true);
        preprocessor.processProposal(proposal);
        verify(syncQueues, never()).add(any());
        verify(forwardTo, times(1)).processProposal(eq(proposal));
    }
}
