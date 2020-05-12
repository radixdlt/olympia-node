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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Manages pending votes for various vertices
 */
public final class PendingVotes {
	private final SortedMap<View, Map<Hash, ValidationState>> state = Maps.newTreeMap();
	private final Hasher hasher;

	@Inject
	public PendingVotes(Hasher hasher) {
		this.hasher = Objects.requireNonNull(hasher);
	}

	/**
	 * Removed any validation states before valid QC's view.
	 *
	 * @param view The view of the last valid QC received.
	 */
	public void removeVotesUpto(View view) {
		this.state.headMap(view.next()).clear();
	}

	/**
	 * Inserts a vote for a given vertex, attempting to form a quorum certificate for that vertex.
	 * <p>
	 * A QC will only be formed if permitted by the {@link ValidatorSet}.
	 *
	 * @param vote The vote to be inserted
	 * @return The generated QC, if any
	 */
	public Optional<QuorumCertificate> insertVote(Vote vote, ValidatorSet validatorSet) {
		final View voteView = vote.getVoteData().getProposed().getView();
		final Hash voteHash = hasher.hash(vote.getVoteData());


		ValidationState validationState = this.state
			.computeIfAbsent(voteView, k -> Maps.newHashMap())
			.computeIfAbsent(voteHash, validatorSet::newValidationState);

		final ECDSASignature signature = vote.getSignature().orElseThrow(() -> new IllegalArgumentException("vote is missing signature"));

		final ECPublicKey voteAuthor = vote.getAuthor();
		// try to form a QC with the added signature according to the requirements
		if (!validationState.addSignature(voteAuthor, signature)) {
			// if no QC could be formed, update pending and return nothing
			return Optional.empty();
		} else {
			// if QC could be formed, remove validation state
			// Could continue to accumulate votes until timeout if desired
			removeVotesUpto(voteView);
			QuorumCertificate qc = new QuorumCertificate(vote.getVoteData(), validationState.signatures());
			return Optional.of(qc);
		}
	}

	@VisibleForTesting
	int stateSize() {
		return this.state.size();
	}
}
