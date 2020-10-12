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
import com.radixdlt.consensus.bft.View;
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
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
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
	private final Map<BFTNode, PreviousVote> previousVotes = Maps.newHashMap();
	private final Hasher hasher;

	public PendingVotes(Hasher hasher) {
		this.hasher = Objects.requireNonNull(hasher);
	}

	/**
	 * Inserts a vote for a given vertex, attempting to form a quorum certificate for that vertex.
	 * <p>
	 * A QC will only be formed if permitted by the {@link BFTValidatorSet}.
	 *
	 * @param vote The vote to be inserted
	 * @return The generated QC, if any
	 */
	public Optional<QuorumCertificate> insertVote(Vote vote, BFTValidatorSet validatorSet) {
		final BFTNode node = vote.getAuthor();
		// Only process for valid validators and signatures
		if (!validatorSet.containsNode(node)) {
			log.info("Ignoring vote from invalid author {}", node::getSimpleName);
			return Optional.empty();
		}

		final TimestampedVoteData timestampedVoteData = vote.getTimestampedVoteData();
		final VoteData voteData = timestampedVoteData.getVoteData();
		final Hash voteDataHash = this.hasher.hash(voteData);
		final View voteView = voteData.getProposed().getView();
		if (!replacePreviousVote(node, voteView, voteDataHash)) {
			return Optional.empty();
		}

		// If there is no equivocation or duplication, we process the vote.
		ValidationState validationState = this.voteState.computeIfAbsent(voteDataHash, k -> validatorSet.newValidationState());

		// try to form a QC with the added signature according to the requirements
		final ECDSASignature signature = vote.getSignature();
		if (!(validationState.addSignature(node, timestampedVoteData.getNodeTimestamp(), signature) && validationState.complete())) {
			return Optional.empty();
		}

		// QC can be formed, so return it
		QuorumCertificate qc = new QuorumCertificate(voteData, validationState.signatures());
		return Optional.of(qc);
	}

	private boolean replacePreviousVote(BFTNode author, View voteView, Hash voteHash) {
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
