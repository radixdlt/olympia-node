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

package com.radixdlt.consensus;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.UInt256;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PendingVotesTest {
	private PendingVotes pendingVotes;
	private Hasher hasher;

	@Before
	public void setup() {
		this.hasher = mock(Hasher.class);
		when(hasher.hash(any())).thenReturn(Hash.random());
		this.pendingVotes = new PendingVotes(hasher);
	}

	@Test
	public void equalsContractForPreviousVote() {
		EqualsVerifier.forClass(PendingVotes.PreviousVote.class)
			.verify();
	}

	@Test
	public void when_inserting_valid_but_unaccepted_votes__then_no_qc_is_returned() {
		Hash vertexId = Hash.random();
		Vote vote1 = makeSignedVoteFor(mock(BFTNode.class), View.genesis(), vertexId);
		Vote vote2 = makeSignedVoteFor(mock(BFTNode.class), View.genesis(), vertexId);

		BFTValidatorSet validatorSet = BFTValidatorSet.from(
			Collections.singleton(BFTValidator.from(vote1.getAuthor(), UInt256.ONE))
		);
		VoteData voteData = mock(VoteData.class);
		BFTHeader proposed = vote1.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		assertThat(this.pendingVotes.insertVote(vote2, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_valid_and_accepted_votes__then_qc_is_formed() {
		BFTNode author = mock(BFTNode.class);
		Vote vote = makeSignedVoteFor(author, View.genesis(), Hash.random());

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(validationState.addSignature(any(), anyLong(), any())).thenReturn(true);
		when(validationState.complete()).thenReturn(true);
		when(validationState.signatures()).thenReturn(signatures);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		VoteData voteData = mock(VoteData.class);
		BFTHeader proposed = vote.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		assertThat(this.pendingVotes.insertVote(vote, validatorSet)).isPresent();
	}

	@Test
	public void when_voting_again__previous_vote_is_removed() {
		BFTNode author = mock(BFTNode.class);
		Vote vote = makeSignedVoteFor(author, View.genesis(), Hash.random());

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(validationState.signatures()).thenReturn(signatures);
		when(validationState.isEmpty()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		VoteData voteData = mock(VoteData.class);
		BFTHeader proposed = vote.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		// Preconditions
		assertThat(this.pendingVotes.insertVote(vote, validatorSet)).isNotPresent();
		assertEquals(1, this.pendingVotes.voteStateSize());
		assertEquals(1, this.pendingVotes.previousVotesSize());

		Vote vote2 = makeSignedVoteFor(author, View.of(1), Hash.random());
		// Need a different hash for this (different) vote
		when(hasher.hash(eq(vote2.getVoteData()))).thenReturn(Hash.random());
		assertThat(this.pendingVotes.insertVote(vote2, validatorSet)).isNotPresent();
		assertEquals(1, this.pendingVotes.voteStateSize());
		assertEquals(1, this.pendingVotes.previousVotesSize());
	}

	private Vote makeSignedVoteFor(BFTNode author, View parentView, Hash vertexId) {
		Vote vote = makeVoteWithoutSignatureFor(author, parentView, vertexId);
		when(vote.getSignature()).thenReturn(new ECDSASignature());
		return vote;
	}

	private Vote makeVoteWithoutSignatureFor(BFTNode author, View parentView, Hash vertexId) {
		Vote vote = mock(Vote.class);
		BFTHeader proposed = new BFTHeader(parentView.next(), vertexId, mock(LedgerHeader.class));
		BFTHeader parent = new BFTHeader(parentView, Hash.random(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(proposed, parent, null);
		TimestampedVoteData timestampedVoteData = new TimestampedVoteData(voteData, 123456L);
		when(vote.getVoteData()).thenReturn(voteData);
		when(vote.getTimestampedVoteData()).thenReturn(timestampedVoteData);
		when(vote.getAuthor()).thenReturn(author);
		return vote;
	}
}
