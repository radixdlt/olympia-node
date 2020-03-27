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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.validators.ValidationResult;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import java.util.Collections;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PendingVotesTest {
	@Test
	public void when_inserting_a_vote_without_signature__then_exception_is_thrown() {
		PendingVotes pendingVotes = new PendingVotes();
		Vote voteWithoutSignature = mock(Vote.class);
		when(voteWithoutSignature.getVertexMetadata()).thenReturn(mock(VertexMetadata.class));
		when(voteWithoutSignature.getSignature()).thenReturn(Optional.empty());

		assertThatThrownBy(() -> pendingVotes.insertVote(voteWithoutSignature, mock(ValidatorSet.class)));
	}

	@Test
	public void when_inserting_valid_but_unaccepted_votes__then_no_qc_is_returned() {
		Hash vertexId = Hash.random();
		Vote vote1 = makeVoteFor(vertexId);
		Vote vote2 = makeVoteFor(vertexId);
		ValidatorSet validatorSet = ValidatorSet.from(Collections.singleton(Validator.from(vote1.getAuthor())));
		PendingVotes pendingVotes = new PendingVotes();
		assertThat(pendingVotes.insertVote(vote2, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_valid_and_accepted_votes__then_qc_is_formed() {
		Hash vertexId = Hash.random();
		Vote vote1 = makeVoteFor(vertexId);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.validate(any(), any())).thenReturn(ValidationResult.passed(ImmutableList.of(mock(Validator.class))));
		PendingVotes pendingVotes = new PendingVotes();
		assertThat(pendingVotes.insertVote(vote1, validatorSet)).isPresent();
	}

	private Vote makeVoteFor(Hash vertexId) {
		Vote vote = mock(Vote.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(vertexId);
		when(vote.getVertexMetadata()).thenReturn(vertexMetadata);
		when(vote.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		when(vote.getAuthor()).thenReturn(ECKeyPair.generateNew().getPublicKey());
		return vote;
	}
}
