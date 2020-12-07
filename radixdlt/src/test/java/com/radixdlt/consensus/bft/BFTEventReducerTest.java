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
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import org.junit.Before;
import org.junit.Test;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BFTEventReducerTest {

    private Hasher hasher = mock(Hasher.class);
    private RemoteEventDispatcher<Vote> voteDispatcher = rmock(RemoteEventDispatcher.class);
    private PendingVotes pendingVotes = mock(PendingVotes.class);
    private BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
    private VertexStore vertexStore = mock(VertexStore.class);
    private SafetyRules safetyRules = mock(SafetyRules.class);
    private Pacemaker pacemaker = mock(Pacemaker.class);
    private EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher = rmock(EventDispatcher.class);
    private EventDispatcher<NoVote> noVoteEventDispatcher = rmock(EventDispatcher.class);

    private BFTEventReducer bftEventReducer;

    @Before
    public void setUp() {
        this.bftEventReducer = new BFTEventReducer(
            this.pacemaker,
            this.vertexStore,
            this.viewQuorumReachedEventDispatcher,
            this.noVoteEventDispatcher,
            this.voteDispatcher,
            this.hasher,
            this.safetyRules,
            this.validatorSet,
            this.pendingVotes,
            mock(ViewUpdate.class)
        );
    }

    @Test
    public void when_process_vote_with_quorum_wrong_view__then_ignored() {
        Vote vote = mock(Vote.class);
        when(vote.getView()).thenReturn(View.of(1));
        this.bftEventReducer.processViewUpdate(ViewUpdate.create(View.of(3), mock(HighQC.class), mock(BFTNode.class), mock(BFTNode.class)));
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

        when(this.pendingVotes.insertVote(any(), any(), any())).thenReturn(VoteProcessingResult.qcQuorum(qc));
        when(this.vertexStore.highQC()).thenReturn(highQc);

        // Move to view 1
        this.bftEventReducer.processViewUpdate(ViewUpdate.create(View.of(1), highQc, mock(BFTNode.class), mock(BFTNode.class)));

        this.bftEventReducer.processVote(vote);

        verify(this.viewQuorumReachedEventDispatcher, times(1)).dispatch(any());
        verify(this.pendingVotes, times(1)).insertVote(eq(vote), any(), any());
        verifyNoMoreInteractions(this.pendingVotes);
    }
}
