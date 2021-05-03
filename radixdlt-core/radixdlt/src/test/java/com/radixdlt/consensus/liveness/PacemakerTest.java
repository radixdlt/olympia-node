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

import java.util.List;
import java.util.Optional;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.network.TimeSupplier;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import org.mockito.ArgumentCaptor;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
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
	private BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
	private VertexStore vertexStore = mock(VertexStore.class);
	private SafetyRules safetyRules = mock(SafetyRules.class);
	private PacemakerTimeoutCalculator timeoutCalculator = mock(PacemakerTimeoutCalculator.class);
	private NextTxnsGenerator nextTxnsGenerator = mock(NextTxnsGenerator.class);
	private RemoteEventDispatcher<Vote> voteDispatcher = rmock(RemoteEventDispatcher.class);
	private RemoteEventDispatcher<Proposal> proposalDispatcher = rmock(RemoteEventDispatcher.class);
	private EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher = rmock(EventDispatcher.class);
	private ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender = rmock(ScheduledEventDispatcher.class);
	private TimeSupplier timeSupplier = mock(TimeSupplier.class);

	private Pacemaker pacemaker;

	@Before
	public void setUp() {
		HighQC highQC = mock(HighQC.class);
		QuorumCertificate committedQc = mock(QuorumCertificate.class);
		when(committedQc.getView()).thenReturn(View.of(0));
		when(highQC.highestCommittedQC()).thenReturn(committedQc);

		ViewUpdate initialViewUpdate =
			ViewUpdate.create(View.of(0), highQC, mock(BFTNode.class), mock(BFTNode.class));

		this.pacemaker = new Pacemaker(
			this.self,
			this.counters,
			this.validatorSet,
			this.vertexStore,
			this.safetyRules,
			this.timeoutDispatcher,
			this.timeoutSender,
			this.timeoutCalculator,
			this.nextTxnsGenerator,
			this.proposalDispatcher,
			hasher,
			voteDispatcher,
			timeSupplier,
			initialViewUpdate,
			new SystemCountersImpl()
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

		ViewUpdate viewUpdate = ViewUpdate.create(View.of(0), mock(HighQC.class), mock(BFTNode.class), mock(BFTNode.class));
		this.pacemaker.processLocalTimeout(ScheduledLocalTimeout.create(viewUpdate, 0L));

		verify(this.voteDispatcher, times(1)).dispatch(eq(validators), eq(lastVoteWithTimeout));
		verifyNoMoreInteractions(this.vertexStore);
		verify(this.safetyRules, times(1)).getLastVote(view);
		verify(this.safetyRules, times(1)).timeoutVote(lastVote);
		verifyNoMoreInteractions(this.safetyRules);
	}

    @Test
    public void when_local_timeout__then_send_empty_vote_if_no_previous() {
		HighQC viewUpdateHighQc = mock(HighQC.class);
		QuorumCertificate committedQc = mock(QuorumCertificate.class);
		QuorumCertificate highestQc = mock(QuorumCertificate.class);
		when(viewUpdateHighQc.highestCommittedQC()).thenReturn(committedQc);
		when(viewUpdateHighQc.highestQC()).thenReturn(highestQc);
		BFTHeader highestQcProposed = mock(BFTHeader.class);
		HashCode highQcParentVertexId = mock(HashCode.class);
		when(highestQcProposed.getVertexId()).thenReturn(highQcParentVertexId);
		when(highestQc.getProposed()).thenReturn(highestQcProposed);
		when(committedQc.getView()).thenReturn(View.of(0));
		ViewUpdate viewUpdate = ViewUpdate.create(View.of(1), viewUpdateHighQc, mock(BFTNode.class), mock(BFTNode.class));
		this.pacemaker.processViewUpdate(viewUpdate);
		View view = View.of(1);
		Vote emptyVote = mock(Vote.class);
		Vote emptyVoteWithTimeout = mock(Vote.class);
		ImmutableSet<BFTNode> validators = rmock(ImmutableSet.class);
		BFTHeader bftHeader = mock(BFTHeader.class);
		HighQC highQC = mock(HighQC.class);
		BFTInsertUpdate bftInsertUpdate = mock(BFTInsertUpdate.class);
		when(bftInsertUpdate.getHeader()).thenReturn(bftHeader);
		PreparedVertex preparedVertex = mock(PreparedVertex.class);
		VerifiedVertexStoreState vertexStoreState = mock(VerifiedVertexStoreState.class);
		when(vertexStoreState.getHighQC()).thenReturn(highQC);
		when(bftInsertUpdate.getInserted()).thenReturn(preparedVertex);
		when(bftInsertUpdate.getVertexStoreState()).thenReturn(vertexStoreState);
		when(preparedVertex.getId()).thenReturn(hasher.hash(UnverifiedVertex.createVertex(highestQc, view, List.of())));

		when(this.safetyRules.getLastVote(view)).thenReturn(Optional.empty());
		when(this.safetyRules.createVote(any(), any(), anyLong(), any())).thenReturn(emptyVote);
		when(this.safetyRules.timeoutVote(emptyVote)).thenReturn(emptyVoteWithTimeout);
		when(this.validatorSet.nodes()).thenReturn(validators);

		when(this.vertexStore.getPreparedVertex(any())).thenReturn(Optional.empty());

		this.pacemaker.processLocalTimeout(ScheduledLocalTimeout.create(
			ViewUpdate.create(View.of(1), mock(HighQC.class), mock(BFTNode.class), mock(BFTNode.class)), 0L));

		this.pacemaker.processBFTUpdate(bftInsertUpdate);

		verify(this.voteDispatcher, times(1)).dispatch(eq(validators), eq(emptyVoteWithTimeout));
		verify(this.safetyRules, times(1)).getLastVote(view);
		verify(this.safetyRules, times(1)).createVote(any(), any(), anyLong(), any());
		verify(this.safetyRules, times(1)).timeoutVote(emptyVote);
		verifyNoMoreInteractions(this.safetyRules);

		verify(this.vertexStore, times(1)).getPreparedVertex(any());

		ArgumentCaptor<VerifiedVertex> insertVertexCaptor = ArgumentCaptor.forClass(VerifiedVertex.class);
		verify(this.vertexStore, times(1)).insertVertex(insertVertexCaptor.capture());
		assertEquals(insertVertexCaptor.getValue().getParentId(), highQcParentVertexId);

		verifyNoMoreInteractions(this.vertexStore);
    }

	@Test
	public void when_local_timeout_for_non_current_view__then_ignored() {
		this.pacemaker.processLocalTimeout(ScheduledLocalTimeout.create(
			ViewUpdate.create(View.of(1), mock(HighQC.class), mock(BFTNode.class), mock(BFTNode.class)), 0L));
		verifyNoMoreInteractions(this.safetyRules);
	}
}
