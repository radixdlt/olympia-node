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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

/**
 * Manages pending votes for various vertices.
 * <p>
 * This class is NOT thread-safe.
 * <p>
 * This class is security critical (signature checks, validator set membership checks).
 */
@NotThreadSafe
@SecurityCritical({ SecurityKind.SIG_VERIFY, SecurityKind.GENERAL })
public final class PendingVotes {
	private static final Logger log = LogManager.getLogger();

	@VisibleForTesting
	// Make sure equals tester can access.
	static final class PreviousVote {
		private final View view;
		private final Hash hash;

		PreviousVote(View view, Hash hash) {
			this.view = view;
			this.hash = hash;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.view, this.hash);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof PreviousVote) {
				PreviousVote that = (PreviousVote) obj;
				return Objects.equals(this.view, that.view) && Objects.equals(this.hash, that.hash);
			}
			return false;
		}
	}

	private final Map<Hash, ValidationState> voteState = Maps.newHashMap();
	private final Map<ECPublicKey, PreviousVote> previousVotes = Maps.newHashMap();
	private final Hasher hasher;

	public PendingVotes(Hasher hasher) {
		this.hasher = Objects.requireNonNull(hasher);
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
		final ECPublicKey voteAuthor = vote.getAuthor();
		final VoteData voteData = vote.getVoteData();
		final Hash voteHash = this.hasher.hash(voteData);
		final ECDSASignature signature = vote.getSignature().orElseThrow(() -> new IllegalArgumentException("vote is missing signature"));
		// Only process for valid validators and signatures
		if (validatorSet.containsKey(voteAuthor)) {
			if (voteAuthor.verify(voteHash, signature)) {
				final View voteView = voteData.getProposed().getView();
				if (replacePreviousVote(voteAuthor, voteView, voteHash)) {
					// If there is no equivocation or duplication, we process the vote.
					ValidationState validationState = this.voteState.computeIfAbsent(voteHash, k -> validatorSet.newValidationState());

					// try to form a QC with the added signature according to the requirements
					if (validationState.addSignature(voteAuthor, signature) && validationState.complete()) {
						// QC can be formed, so return it
						QuorumCertificate qc = new QuorumCertificate(vote.getVoteData(), validationState.signatures());
						return Optional.of(qc);
					}
				}
			} else {
				log.info("Ignoring invalid signature from author {}", () -> hostId(voteAuthor));
			}
		} else {
			log.info("Ignoring vote from invalid author {}", () -> hostId(voteAuthor));
		}
		// No QC could be formed, so return nothing
		return Optional.empty();
	}

	private boolean replacePreviousVote(ECPublicKey author, View voteView, Hash voteHash) {
		PreviousVote thisVote = new PreviousVote(voteView, voteHash);
		PreviousVote previousVote = this.previousVotes.put(author, thisVote);
		if (previousVote == null) {
			// No previous vote for this author, all good here
			return true;
		}

		if (thisVote.equals(previousVote)) {
			// Just going to ignore this duplicate vote for now.
			// However, we can't count duplicate votes multiple times.
			return false;
		}

		// Prune last pending vote from the pending votes.
		// This limits the number of pending vertices that are in the pipeline.
		ValidationState validationState = this.voteState.get(previousVote.hash);
		if (validationState != null) {
			validationState.removeSignature(author);
			if (validationState.isEmpty()) {
				this.voteState.remove(previousVote.hash);
			}
		}

		// If the validator already voted in this view for something else,
		// then it should be slashed, once we have that infrastructure in place.
		// In any case, equivocating votes should not be counted.
		return !voteView.equals(previousVote.view);
	}

	private static String hostId(ECPublicKey author) {
		return author.euid().toString().substring(0, 6);
	}

	@VisibleForTesting
	// Greybox stuff for testing
	int voteStateSize() {
		return this.voteState.size();
	}

	@VisibleForTesting
	// Greybox stuff for testing
	int previousVotesSize() {
		return this.previousVotes.size();
	}
}
