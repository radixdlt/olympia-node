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

import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.UInt256;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
	public void when_inserting_a_vote_without_signature__then_exception_is_thrown() {
		Vote voteWithoutSignature = makeUnsignedVoteFor(Hash.random());

		VoteData voteData = mock(VoteData.class);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		VertexMetadata proposed = voteWithoutSignature.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		this.pendingVotes.startVotingOn(voteData, validatorSet);
		assertThatThrownBy(() -> pendingVotes.insertVote(voteWithoutSignature)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_inserting_valid_but_unaccepted_votes__then_no_qc_is_returned() {
		Hash vertexId = Hash.random();
		Vote vote1 = makeSignedVoteFor(vertexId);
		Vote vote2 = makeSignedVoteFor(vertexId);

		ValidatorSet validatorSet = ValidatorSet.from(Collections.singleton(Validator.from(vote1.getAuthor(), UInt256.ONE)));
		VoteData voteData = mock(VoteData.class);
		VertexMetadata proposed = vote1.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		this.pendingVotes.startVotingOn(voteData, validatorSet);
		assertThat(this.pendingVotes.insertVote(vote2)).isEmpty();
	}

	@Test
	public void when_inserting_valid_and_accepted_votes__then_qc_is_formed() {
		Vote vote = makeSignedVoteFor(Hash.random());

		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		ECDSASignatures signatures = mock(ECDSASignatures.class);
		when(validationState.addSignature(any(), any())).thenReturn(true);
		when(validationState.signatures()).thenReturn(signatures);
		when(validatorSet.newValidationState(any())).thenReturn(validationState);

		VoteData voteData = mock(VoteData.class);
		VertexMetadata proposed = vote.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		this.pendingVotes.startVotingOn(voteData, validatorSet);
		assertThat(this.pendingVotes.insertVote(vote)).isPresent();
	}

	@Test
	public void when_inserting_vote_with_wrong_hash__then_no_qc_is_returned() {
		Vote vote = makeSignedVoteFor(Hash.random());

		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		ECDSASignatures signatures = mock(ECDSASignatures.class);
		when(validationState.addSignature(any(), any())).thenReturn(true);
		when(validationState.signatures()).thenReturn(signatures);
		when(validatorSet.newValidationState(any())).thenReturn(validationState);

		VoteData voteData = mock(VoteData.class);
		VertexMetadata proposed = vote.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);
		when(this.hasher.hash(voteData)).thenReturn(Hash.random());

		this.pendingVotes.startVotingOn(voteData, validatorSet);
		assertThat(this.pendingVotes.insertVote(vote)).isNotPresent();
	}

	@Test
	public void when_inserting_vote_for_unstarted_round__then_no_qc_is_returned() {
		Vote vote = makeSignedVoteFor(Hash.random());
		assertThat(this.pendingVotes.insertVote(vote)).isNotPresent();
	}

	@Test
	public void when_inserting_vote_for_completed_round__then_no_qc_is_returned() {
		Vote vote = makeSignedVoteFor(Hash.random());
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		ECDSASignatures signatures = mock(ECDSASignatures.class);
		when(validationState.addSignature(any(), any())).thenReturn(true);
		when(validationState.signatures()).thenReturn(signatures);
		when(validatorSet.newValidationState(any())).thenReturn(validationState);

		VoteData voteData = mock(VoteData.class);
		VertexMetadata proposed = vote.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		this.pendingVotes.startVotingOn(voteData, validatorSet);
		this.pendingVotes.finishVotingFor(vote.getVoteData().getProposed().getView());
		assertThat(this.pendingVotes.insertVote(vote)).isNotPresent();
	}

	private Vote makeUnsignedVoteFor(Hash vertexId) {
		Vote vote = makeVoteWithoutSignatureFor(vertexId);
		when(vote.getSignature()).thenReturn(Optional.empty());
		return vote;
	}

	private Vote makeSignedVoteFor(Hash vertexId) {
		Vote vote = makeVoteWithoutSignatureFor(vertexId);
		when(vote.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		return vote;
	}

	private Vote makeVoteWithoutSignatureFor(Hash vertexId) {
		Vote vote = mock(Vote.class);
		VertexMetadata proposed = new VertexMetadata(View.of(1), vertexId, 1);
		VertexMetadata parent = new VertexMetadata(View.of(0), Hash.random(), 0);
		VoteData voteData = new VoteData(proposed, parent);
		when(vote.getVoteData()).thenReturn(voteData);
		when(vote.getAuthor()).thenReturn(ECKeyPair.generateNew().getPublicKey());
		return vote;
	}
}
