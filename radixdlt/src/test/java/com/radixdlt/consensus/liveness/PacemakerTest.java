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

package com.radixdlt.consensus.liveness;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;

import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.TimeSupplier;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class PacemakerTest {

	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	private BFTNode self = mock(BFTNode.class);
	private SystemCounters counters = mock(SystemCounters.class);
	private HashSigner signer = mock(HashSigner.class);
	private BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
	private VertexStore vertexStore = mock(VertexStore.class);
	private ProposerElection proposerElection = mock(ProposerElection.class);
	private SafetyRules safetyRules = mock(SafetyRules.class);
	private PacemakerTimeoutSender timeoutSender = mock(PacemakerTimeoutSender.class);
	private PacemakerInfoSender infoSender = mock(PacemakerInfoSender.class);
	private PacemakerState pacemakerState = mock(PacemakerState.class);
	private PacemakerTimeoutCalculator timeoutCalculator = mock(PacemakerTimeoutCalculator.class);
	private NextCommandGenerator nextCommandGenerator = mock(NextCommandGenerator.class);
	private ProposalBroadcaster proposalBroadcaster = mock(ProposalBroadcaster.class);
	private RemoteEventDispatcher<Vote> voteDispatcher = rmock(RemoteEventDispatcher.class);
	private TimeSupplier timeSupplier = mock(TimeSupplier.class);

	private Pacemaker pacemaker;

	@Before
	public void setUp() {
		this.pacemaker = new Pacemaker(
			this.self,
			this.counters,
			this.validatorSet,
			this.vertexStore,
			this.safetyRules,
			this.infoSender,
			this.pacemakerState,
			this.timeoutSender,
			this.timeoutCalculator,
			this.nextCommandGenerator,
			this.proposalBroadcaster,
			this.proposerElection,
			hasher,
            voteDispatcher,
            timeSupplier
		);
	}

    @Test
    public void when_local_timeout__then_resend_previous_vote() {
	    View view = View.of(0);
	    Vote lastVote = mock(Vote.class);
	    Vote lastVoteWithTimeout = mock(Vote.class);
        ImmutableSet<BFTNode> validators = rmock(ImmutableSet.class);

        when(this.safetyRules.getLastVote(view)).thenReturn(Optional.of(lastVote));
        when(this.safetyRules.timeoutVote(lastVote)).thenReturn(lastVoteWithTimeout);
        when(this.validatorSet.nodes()).thenReturn(validators);

        this.pacemaker.processLocalTimeout(view);

        verify(this.voteDispatcher, times(1)).dispatch(eq(validators), eq(lastVoteWithTimeout));
        verifyNoMoreInteractions(this.vertexStore);
        verify(this.safetyRules, times(1)).getLastVote(view);
        verify(this.safetyRules, times(1)).timeoutVote(lastVote);
        verifyNoMoreInteractions(this.safetyRules);
    }

    @Test
    public void when_local_timeout__then_send_empty_vote_if_no_previous() {
	    this.pacemaker.processViewUpdate(new ViewUpdate(View.of(1), View.of(0)));
        View view = View.of(1);
        Vote emptyVote = mock(Vote.class);
        Vote emptyVoteWithTimeout = mock(Vote.class);
        ImmutableSet<BFTNode> validators = rmock(ImmutableSet.class);
        BFTHeader bftHeader = mock(BFTHeader.class);
        HighQC highQC = mock(HighQC.class);
        QuorumCertificate qc = mock(QuorumCertificate.class);
        when(highQC.highestQC()).thenReturn(qc);

        when(this.vertexStore.highQC()).thenReturn(highQC);
        when(this.safetyRules.getLastVote(view)).thenReturn(Optional.empty());
        when(this.safetyRules.createVote(any(), any(), anyLong(), any())).thenReturn(emptyVote);
        when(this.safetyRules.timeoutVote(emptyVote)).thenReturn(emptyVoteWithTimeout);
        when(this.validatorSet.nodes()).thenReturn(validators);
        when(this.vertexStore.insertVertex(any())).thenReturn(Optional.of(bftHeader));

        this.pacemaker.processLocalTimeout(view);

        verify(this.voteDispatcher, times(1)).dispatch(eq(validators), eq(emptyVoteWithTimeout));
        verify(this.safetyRules, times(1)).getLastVote(view);
        verify(this.safetyRules, times(1)).createVote(any(), any(), anyLong(), any());
        verify(this.safetyRules, times(1)).timeoutVote(emptyVote);
        verifyNoMoreInteractions(this.safetyRules);
    }

	@Test
	public void when_local_timeout_for_non_current_view__then_ignored() {
		this.pacemaker.processLocalTimeout(View.of(1));
		verifyNoMoreInteractions(this.proposerElection);
		verifyNoMoreInteractions(this.safetyRules);
		verifyNoMoreInteractions(this.pacemakerState);
	}
}
