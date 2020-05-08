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

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Pair;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages pending votes for various vertices
 */
public final class PendingVotes {
	private static final Logger log = LogManager.getLogger();

	private final HashMap<View, Pair<Hash, ValidationState>> state = Maps.newHashMap();
	private final Hasher hasher;

	@Inject
	public PendingVotes(Hasher hasher) {
		this.hasher = Objects.requireNonNull(hasher);
	}

	/**
	 * Prepare for a round of voting on the specified vote data by the specified validator set.
	 *
	 * @param voteData The data to be voted on
	 * @param validatorSet The {@link ValidatorSet} that will be voting
	 */
	public void startVotingOn(VoteData voteData, ValidatorSet validatorSet) {
		View voteView = voteData.getProposed().getView();
		final Hash voteHash = hasher.hash(voteData);
		this.state.put(voteView, Pair.of(voteHash,  validatorSet.newValidationState(voteHash)));
	}

	/**
	 * Finish a round of voting for the specified view.
	 *
	 * @param view The view to finish voting for
	 */
	public void finishVotingFor(View view) {
		this.state.remove(view);
	}

	/**
	 * Inserts a vote for a given vertex, attempting to form a quorum certificate for that vertex.
	 *
	 * A QC will only be formed if permitted by the {@link ValidatorSet}.
	 * @param vote The vote to be inserted
	 * @return The generated QC, if any
	 */
	public Optional<QuorumCertificate> insertVote(Vote vote) {
		View voteView = vote.getVoteData().getProposed().getView();
		Pair<Hash, ValidationState> votingState = this.state.get(voteView);

		final ECPublicKey voteAuthor = vote.getAuthor();
		if (votingState == null) {
			String replicaId = replicaId(voteAuthor);
			log.warn("Ignoring invalid vote from {} for view {}", replicaId, voteView);
			return Optional.empty();
		}

		final Hash voteHash = hasher.hash(vote.getVoteData());
		if (!voteHash.equals(votingState.getFirst())) {
			String replicaId = replicaId(voteAuthor);
			String wantedHash = votingState.getFirst().toString();
			log.warn("Ignoring invalid vote from {} for view {} for unknown hash {} (wanted {})", replicaId, voteView, voteHash, wantedHash);
			return Optional.empty();
		}

		ECDSASignature signature = vote.getSignature().orElseThrow(() -> new IllegalArgumentException("vote is missing signature"));

		ValidationState validationState = votingState.getSecond();
		// try to form a QC with the added signature according to the requirements
		if (!validationState.addSignature(voteAuthor, signature)) {
			// if no QC could be formed, update pending and return nothing
			return Optional.empty();
		} else {
			// if QC could be formed, remove validation state
			// Could continue to accumulate votes until timeout if desired
			finishVotingFor(voteView);
			QuorumCertificate qc = new QuorumCertificate(vote.getVoteData(), validationState.signatures());
			return Optional.of(qc);
		}
	}

	private static String replicaId(ECPublicKey key) {
		return key.euid().toString().substring(0, 6);
	}
}
