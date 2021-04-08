/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.safety;

import org.junit.Before;
import org.junit.Test;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.SafetyState.Builder;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hasher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This tests that the {@link SafetyRules} implementation obeys HotStuff's safety and commit rules.
 */
public class SafetyRulesTest {
	private SafetyState safetyState;
	private SafetyRules safetyRules;

	@Before
	public void setup() {
		this.safetyState = mock(SafetyState.class);
		Hasher hasher = mock(Hasher.class);
		when(hasher.hash(any())).thenReturn(mock(HashCode.class));
		HashSigner hashSigner = mock(HashSigner.class);
		when(hashSigner.sign(any(HashCode.class))).thenReturn(ECDSASignature.zeroSignature());
		this.safetyRules = new SafetyRules(mock(BFTNode.class), safetyState, mock(PersistentSafetyStateStore.class), hasher, hashSigner);
	}

	@Test
	public void when_vote_on_same_view__then_exception_is_thrown() {
		View view = mock(View.class);
		when(view.lte(view)).thenReturn(true);
		when(safetyState.getLastVotedView()).thenReturn(view);
		VerifiedVertex vertex = mock(VerifiedVertex.class);
		when(vertex.getView()).thenReturn(view);

		assertThat(this.safetyRules.voteFor(vertex, mock(BFTHeader.class), 0L, mock(HighQC.class)))
			.isEmpty();
	}

	@Test
	public void when_vote_with_qc_on_different_locked_view__then_exception_is_thrown() {
		Hasher hasher = mock(Hasher.class);
		when(hasher.hash(any())).thenReturn(mock(HashCode.class));
		HashSigner hashSigner = mock(HashSigner.class);
		when(hashSigner.sign(any(HashCode.class))).thenReturn(ECDSASignature.zeroSignature());

		Vote lastVote = mock(Vote.class);
		when(lastVote.getView()).thenReturn(View.of(1));

		SafetyRules safetyRules = new SafetyRules(
			BFTNode.random(),
			new SafetyState(View.of(2), Optional.of(lastVote)),
			mock(PersistentSafetyStateStore.class),
			hasher,
			hashSigner
		);

		VerifiedVertex vertex = mock(VerifiedVertex.class);
		when(vertex.getView()).thenReturn(View.of(3));
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getView()).thenReturn(View.of(0));
		when(vertex.getParentHeader()).thenReturn(parent);

		assertThat(safetyRules.voteFor(vertex, mock(BFTHeader.class), 0L, mock(HighQC.class)))
			.isEmpty();
	}

	@Test
	public void when_vote_on_proposal_after_genesis__then_returned_vote_has_no_commit() {
		when(safetyState.getLastVotedView()).thenReturn(View.of(0));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));
		VerifiedVertex vertex = mock(VerifiedVertex.class);
		when(vertex.hasDirectParent()).thenReturn(true);
		when(vertex.touchesGenesis()).thenReturn(true);
		when(vertex.parentHasDirectParent()).thenReturn(true);
		when(vertex.getView()).thenReturn(View.of(1));
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getView()).thenReturn(View.of(0));
		when(vertex.getParentHeader()).thenReturn(parent);
		BFTHeader grandParent = mock(BFTHeader.class);
		when(grandParent.getView()).thenReturn(mock(View.class));
		when(vertex.getGrandParentHeader()).thenReturn(grandParent);
		BFTHeader header = mock(BFTHeader.class);
		Optional<Vote> voteMaybe = safetyRules.voteFor(vertex, header, 0L, mock(HighQC.class));
		assertThat(voteMaybe).isNotEmpty();
		Vote vote = voteMaybe.get();
		assertThat(vote.getVoteData().getProposed()).isEqualTo(header);
		assertThat(vote.getVoteData().getParent()).isEqualTo(parent);
		assertThat(vote.getVoteData().getCommitted()).isEmpty();
	}

	@Test
	public void when_vote_on_proposal_two_after_genesis__then_returned_vote_has_no_commit() {
		when(safetyState.getLastVotedView()).thenReturn(View.of(1));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));
		VerifiedVertex proposal = mock(VerifiedVertex.class);
		when(proposal.touchesGenesis()).thenReturn(true);
		when(proposal.hasDirectParent()).thenReturn(true);
		when(proposal.parentHasDirectParent()).thenReturn(true);
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getView()).thenReturn(View.of(1));
		when(proposal.getParentHeader()).thenReturn(parent);
		when(proposal.getView()).thenReturn(View.of(2));
		BFTHeader grandParent = mock(BFTHeader.class);
		when(grandParent.getView()).thenReturn(mock(View.class));
		when(proposal.getGrandParentHeader()).thenReturn(grandParent);
		Optional<Vote> voteMaybe = safetyRules.voteFor(proposal, mock(BFTHeader.class), 0L, mock(HighQC.class));
		assertThat(voteMaybe).isNotEmpty();
		Vote vote = voteMaybe.get();
		assertThat(vote.getVoteData().getCommitted()).isEmpty();
	}

	@Test
	public void when_vote_on_proposal_three_after_genesis__then_returned_vote_has_commit() {
		when(safetyState.getLastVotedView()).thenReturn(View.of(1));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));

		VerifiedVertex proposal = mock(VerifiedVertex.class);
		when(proposal.touchesGenesis()).thenReturn(false);
		when(proposal.hasDirectParent()).thenReturn(true);
		when(proposal.parentHasDirectParent()).thenReturn(true);
		BFTHeader grandparentHeader = mock(BFTHeader.class);
		when(grandparentHeader.getView()).thenReturn(mock(View.class));
		when(proposal.getGrandParentHeader()).thenReturn(grandparentHeader);
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getView()).thenReturn(View.of(2));
		when(proposal.getParentHeader()).thenReturn(parent);
		when(proposal.getView()).thenReturn(View.of(3));

		Optional<Vote> voteMaybe = safetyRules.voteFor(proposal, mock(BFTHeader.class), 0L, mock(HighQC.class));
		assertThat(voteMaybe).isNotEmpty();
		Vote vote = voteMaybe.get();
		assertThat(vote.getVoteData().getCommitted()).hasValue(grandparentHeader);
	}

	@Test
	public void when_vote_on_proposal_three_after_genesis_with_skip__then_returned_vote_has_no_commit() {
		when(safetyState.getLastVotedView()).thenReturn(View.of(1));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));

		VerifiedVertex proposal = mock(VerifiedVertex.class);
		when(proposal.touchesGenesis()).thenReturn(false);
		when(proposal.hasDirectParent()).thenReturn(false);
		when(proposal.parentHasDirectParent()).thenReturn(true);
		BFTHeader parent = mock(BFTHeader.class);
		when(parent.getView()).thenReturn(View.of(2));
		when(proposal.getParentHeader()).thenReturn(parent);
		when(proposal.getView()).thenReturn(View.of(4));
		BFTHeader grandParent = mock(BFTHeader.class);
		when(grandParent.getView()).thenReturn(mock(View.class));
		when(proposal.getGrandParentHeader()).thenReturn(grandParent);

		Optional<Vote> voteMaybe = safetyRules.voteFor(proposal, mock(BFTHeader.class), 0L, mock(HighQC.class));
		assertThat(voteMaybe).isNotEmpty();
		Vote vote = voteMaybe.get();
		assertThat(vote.getVoteData().getCommitted()).isEmpty();
	}

	@Test
	public void when_timeout_already_timed_out_vote_than_the_same_vote_is_returned() {
		Vote vote = mock(Vote.class);
		when(vote.isTimeout()).thenReturn(true);
		assertEquals(vote, safetyRules.timeoutVote(vote));
	}

	@Test
	public void when_timeout_a_vote_than_it_has_a_timeout_signature() {
		Vote vote = mock(Vote.class);
		Vote voteWithTimeout = mock(Vote.class);
		when(vote.getView()).thenReturn(View.of(1));
		when(vote.getEpoch()).thenReturn(1L);
		when(vote.withTimeoutSignature(any())).thenReturn(voteWithTimeout);
		when(vote.isTimeout()).thenReturn(false);

		Builder builder = mock(Builder.class);
		when(builder.lastVote(any())).thenReturn(builder);
		when(builder.build()).thenReturn(this.safetyState);
		when(safetyState.toBuilder()).thenReturn(builder);

		Vote resultVote = safetyRules.timeoutVote(vote);
		verify(vote, times(1)).withTimeoutSignature(any());
		assertEquals(voteWithTimeout, resultVote);
	}
}
