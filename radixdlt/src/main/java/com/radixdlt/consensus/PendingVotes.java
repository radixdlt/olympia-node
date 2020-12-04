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

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import com.radixdlt.consensus.bft.VoteProcessingResult;
import com.radixdlt.consensus.bft.VoteProcessingResult.VoteRejected.VoteRejectedReason;
import com.radixdlt.consensus.liveness.VoteTimeout;
import com.radixdlt.crypto.Hasher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECDSASignature;

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
		private final HashCode hash;

		PreviousVote(View view, HashCode hash) {
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

	private final Map<HashCode, ValidationState> voteState = Maps.newHashMap();
	private final Map<HashCode, ValidationState> timeoutVoteState = Maps.newHashMap();
	private final Map<BFTNode, PreviousVote> previousVotes = Maps.newHashMap();
	private final Hasher hasher;
	private final BFTNode self;

	public PendingVotes(Hasher hasher, BFTNode self) {
		this.hasher = Objects.requireNonNull(hasher);
		this.self = Objects.requireNonNull(self);
	}

	/**
	 * Inserts a vote for a given vertex,
	 * attempting to form either a quorum certificate for that vertex
	 * or a timeout certificate.
	 * A quorum will only be formed if permitted by the {@link BFTValidatorSet}.
	 *
	 * @param vote The vote to be inserted
	 * @return The result of vote processing
	 */
	public VoteProcessingResult insertVote(Vote vote, BFTValidatorSet validatorSet, BFTNode nextProposer) {
		final BFTNode node = vote.getAuthor();
		final TimestampedVoteData timestampedVoteData = vote.getTimestampedVoteData();
		final VoteData voteData = timestampedVoteData.getVoteData();
		final HashCode voteDataHash = this.hasher.hash(voteData);
		final View voteView = voteData.getProposed().getView();

		if (!validatorSet.containsNode(node)) {
			return VoteProcessingResult.rejected(VoteRejectedReason.INVALID_AUTHOR);
		}

		if (!replacePreviousVote(node, voteView, voteDataHash)) {
			return VoteProcessingResult.rejected(VoteRejectedReason.DUPLICATE_VOTE);
		}

		if (!this.self.equals(nextProposer) && !vote.isTimeout()) {
			return VoteProcessingResult.rejected(VoteRejectedReason.UNEXPECTED_VOTE);
		}

		final Optional<QuorumCertificate> maybeQC = processVoteForQC(vote, validatorSet);
		final Optional<TimeoutCertificate> maybeTC = processVoteForTC(vote, validatorSet);

		if (maybeQC.isPresent()) {
			return VoteProcessingResult.qcQuorum(maybeQC.get());
		} else if (maybeTC.isPresent()) {
			return VoteProcessingResult.tcQuorum(maybeTC.get());
		} else {
			return VoteProcessingResult.accepted();
		}
	}

	private Optional<QuorumCertificate> processVoteForQC(Vote vote, BFTValidatorSet validatorSet) {
		final TimestampedVoteData timestampedVoteData = vote.getTimestampedVoteData();
		final VoteData voteData = timestampedVoteData.getVoteData();
		final HashCode voteDataHash = this.hasher.hash(voteData);
		final BFTNode node = vote.getAuthor();

		final ValidationState validationState =
			this.voteState.computeIfAbsent(voteDataHash, k -> validatorSet.newValidationState());

		final boolean signatureAdded =
			validationState.addSignature(node, timestampedVoteData.getNodeTimestamp(), vote.getSignature());

		if (signatureAdded && validationState.complete()) {
			return Optional.of(new QuorumCertificate(voteData, validationState.signatures()));
		} else {
			return Optional.empty();
		}
	}

	private Optional<TimeoutCertificate> processVoteForTC(Vote vote, BFTValidatorSet validatorSet) {
		if (vote.getTimeoutSignature().isEmpty()) {
			return Optional.empty(); // TC can't be formed if vote is not timed out
		}

		final ECDSASignature timeoutSignature = vote.getTimeoutSignature().get();

		final VoteTimeout voteTimeout = VoteTimeout.of(vote);
		final HashCode voteTimeoutHash = this.hasher.hash(voteTimeout);
		final BFTNode node = vote.getAuthor();

		final ValidationState validationState =
			this.timeoutVoteState.computeIfAbsent(voteTimeoutHash, k -> validatorSet.newValidationState());

		final boolean signatureAdded =
			validationState.addSignature(node, vote.getTimestampedVoteData().getNodeTimestamp(), timeoutSignature);

		if (signatureAdded && validationState.complete()) {
			return Optional.of(new TimeoutCertificate(
					voteTimeout.getEpoch(),
					voteTimeout.getView(),
					validationState.signatures()));
		} else {
			return Optional.empty();
		}
	}

	private boolean replacePreviousVote(BFTNode author, View voteView, HashCode voteHash) {
		final PreviousVote thisVote = new PreviousVote(voteView, voteHash);
		final PreviousVote previousVote = this.previousVotes.put(author, thisVote);
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
