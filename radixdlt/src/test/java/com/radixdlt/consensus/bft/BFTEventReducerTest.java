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

import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.TimeSupplier;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BFTEventReducerTest {

    private SystemCounters counters = mock(SystemCounters.class);
    private Hasher hasher = mock(Hasher.class);
    private RemoteEventDispatcher<Vote> voteSender = rmock(RemoteEventDispatcher.class);
    private PendingVotes pendingVotes = mock(PendingVotes.class);
    private BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
    private VertexStore vertexStore = mock(VertexStore.class);
    private ProposerElection proposerElection = mock(ProposerElection.class);
    private SafetyRules safetyRules = mock(SafetyRules.class);
    private Pacemaker pacemaker = mock(Pacemaker.class);
    private EventDispatcher<FormedQC> qcEventDispatcher = rmock(EventDispatcher.class);
    private TimeSupplier timeSupplier = mock(TimeSupplier.class);

    private BFTEventReducer bftEventReducer;

    @Before
    public void setUp() {
        when(proposerElection.getProposer(any())).thenReturn(BFTNode.random());

        this.bftEventReducer = new BFTEventReducer(
            this.pacemaker,
            this.vertexStore,
            this.qcEventDispatcher,
            this.voteSender,
            this.hasher,
            this.timeSupplier,
            this.counters,
            this.safetyRules,
            this.validatorSet,
            this.pendingVotes,
            ViewUpdate.genesis()
        );
    }

    @Test
    public void when_process_vote_equal_last_quorum__then_ignored() {
        Vote vote = mock(Vote.class);
        when(vote.getView()).thenReturn(View.of(0));
        when(this.proposerElection.getProposer(any())).thenReturn(BFTNode.random());
        this.bftEventReducer.processVote(vote);
        verifyNoMoreInteractions(this.pendingVotes);
    }

    @Test
    public void when_process_vote_with_quorum_wrong_view__then_ignored() {
        Vote vote = mock(Vote.class);
        when(vote.getView()).thenReturn(View.of(1));
        when(this.proposerElection.getProposer(any())).thenReturn(BFTNode.random());
        this.bftEventReducer.processViewUpdate(ViewUpdate.create(View.of(3), View.of(2), View.of(2), BFTNode.random(), BFTNode.random()));
        this.bftEventReducer.processVote(vote);
        verifyNoMoreInteractions(this.pendingVotes);
    }

    @Test
    public void when_process_vote_with_quorum__then_processed() {
        BFTNode author = mock(BFTNode.class);
        Vote vote = mock(Vote.class);
        when(vote.getAuthor()).thenReturn(author);

        QuorumCertificate qc = mock(QuorumCertificate.class);
        HighQC highQc = mock(HighQC.class);
        QuorumCertificate highestCommittedQc = mock(QuorumCertificate.class);
        when(highQc.highestCommittedQC()).thenReturn(highestCommittedQc);
        when(vote.getView()).thenReturn(View.of(1));
        when(this.proposerElection.getProposer(any())).thenReturn(BFTNode.random());
        when(this.pendingVotes.insertVote(any(), any())).thenReturn(Optional.of(qc));
        when(this.vertexStore.highQC()).thenReturn(highQc);

        // Move to view 1
        this.bftEventReducer.processViewUpdate(ViewUpdate.create(View.of(1), View.of(0), View.of(0), BFTNode.random(), BFTNode.random()));

        this.bftEventReducer.processVote(vote);

        verify(this.qcEventDispatcher, times(1)).dispatch(any());
        verify(this.pendingVotes, times(1)).insertVote(eq(vote), any());
        verifyNoMoreInteractions(this.pendingVotes);
    }

}