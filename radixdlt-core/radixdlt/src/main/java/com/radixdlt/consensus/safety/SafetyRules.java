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

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.liveness.VoteTimeout;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.safety.SafetyState.Builder;
import com.radixdlt.crypto.ECDSASignature;

import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages safety of the protocol.
 */
public final class SafetyRules {
	private static final Logger logger = LogManager.getLogger();

	private final BFTNode self;
	private final Hasher hasher;
	private final HashSigner signer;
	private final PersistentSafetyStateStore persistentSafetyStateStore;

	private SafetyState state;

	@Inject
	public SafetyRules(
		@Self BFTNode self,
		SafetyState initialState,
		PersistentSafetyStateStore persistentSafetyStateStore,
		Hasher hasher,
		HashSigner signer
	) {
		this.self = self;
		this.state = Objects.requireNonNull(initialState);
		this.persistentSafetyStateStore = Objects.requireNonNull(persistentSafetyStateStore);
		this.hasher = Objects.requireNonNull(hasher);
		this.signer = Objects.requireNonNull(signer);
	}

	private boolean checkLastVoted(VerifiedVertex proposedVertex) {
		// ensure vertex does not violate earlier votes
		if (proposedVertex.getView().lte(this.state.getLastVotedView())) {
			logger.warn("Safety warning: Vertex {} violates earlier vote at view {}",
				proposedVertex,
				this.state.getLastVotedView()
			);
			return false;
		} else {
			return true;
		}
	}

	private boolean checkLocked(VerifiedVertex proposedVertex, Builder nextStateBuilder) {
		if (proposedVertex.getParentHeader().getView().lt(this.state.getLockedView())) {
			logger.warn("Safety warning: Vertex {} does not respect locked view {}",
				proposedVertex,
				this.state.getLockedView()
			);
			return false;
		}

		// pre-commit phase on consecutive qc's proposed vertex
		if (proposedVertex.getGrandParentHeader().getView().compareTo(this.state.getLockedView()) > 0) {
			nextStateBuilder.lockedView(proposedVertex.getGrandParentHeader().getView());
		}
		return true;
	}

	/**
	 * Create a signed proposal from a vertex
	 * @param proposedVertex vertex to sign
	 * @param highestCommittedQC highest known committed QC
	 * @param highestTC highest known TC
	 * @return signed proposal object for consensus
	 */
	public Optional<Proposal> signProposal(
		VerifiedVertex proposedVertex,
		QuorumCertificate highestCommittedQC,
		Optional<TimeoutCertificate> highestTC
	) {
		final Builder safetyStateBuilder = this.state.toBuilder();
		if (!checkLocked(proposedVertex, safetyStateBuilder)) {
			return Optional.empty();
		}

		this.state = safetyStateBuilder.build();

		final ECDSASignature signature = this.signer.sign(proposedVertex.getId());
		return Optional.of(new Proposal(
			proposedVertex.toSerializable(),
			highestCommittedQC,
			this.self,
			signature,
			highestTC
		));
	}

	private static VoteData constructVoteData(VerifiedVertex proposedVertex, BFTHeader proposedHeader) {
		final BFTHeader parent = proposedVertex.getParentHeader();

		// Add a vertex to commit if creating a quorum for the proposed vertex would
		// create three consecutive qcs.
		final BFTHeader toCommit;
		if (proposedVertex.touchesGenesis()
			|| !proposedVertex.hasDirectParent()
			|| !proposedVertex.parentHasDirectParent()
		) {
			toCommit = null;
		} else {
			toCommit = proposedVertex.getGrandParentHeader();
		}

		return new VoteData(proposedHeader, parent, toCommit);
	}

	/**
	 * Vote for a proposed vertex while ensuring that safety invariants are upheld.
	 *
	 * @param proposedVertex The proposed vertex
	 * @param proposedHeader results of vertex execution
	 * @param timestamp timestamp to use for the vote in milliseconds since epoch
	 * @param highQC our current sync state
	 * @return A vote result containing the vote and any committed vertices
	 */
	public Optional<Vote> voteFor(VerifiedVertex proposedVertex, BFTHeader proposedHeader, long timestamp, HighQC highQC) {
		Builder safetyStateBuilder = this.state.toBuilder();

		if (!checkLastVoted(proposedVertex)) {
			return Optional.empty();
		}

		if (!checkLocked(proposedVertex, safetyStateBuilder)) {
			return Optional.empty();
		}

		final Vote vote = createVote(proposedVertex, proposedHeader, timestamp, highQC);

		safetyStateBuilder.lastVote(vote);

		this.state = safetyStateBuilder.build();
		this.persistentSafetyStateStore.commitState(this.state);

		return Optional.of(vote);
	}

	public Vote timeoutVote(Vote vote) {
		if (vote.isTimeout()) { // vote is already timed out
			return vote;
		}

		final VoteTimeout voteTimeout = VoteTimeout.of(vote);
		final HashCode voteTimeoutHash = hasher.hash(voteTimeout);

		final ECDSASignature timeoutSignature = this.signer.sign(voteTimeoutHash);
		final Vote timeoutVote = vote.withTimeoutSignature(timeoutSignature);

		this.state = this.state.toBuilder().lastVote(timeoutVote).build();
		this.persistentSafetyStateStore.commitState(this.state);

		return timeoutVote;
	}

	public Vote createVote(
		VerifiedVertex proposedVertex,
		BFTHeader proposedHeader,
		long timestamp,
		HighQC highQC
	) {
		final VoteData voteData = constructVoteData(proposedVertex, proposedHeader);
		final var voteHash = Vote.getHashOfData(hasher, voteData, timestamp);

		// TODO make signing more robust by including author in signed hash
		final ECDSASignature signature = this.signer.sign(voteHash);
		return new Vote(this.self, voteData, timestamp, signature, highQC, Optional.empty());
	}

	public Optional<Vote> getLastVote(View view) {
		return this.state.getLastVote()
			.filter(lastVote -> lastVote.getView().equals(view));
	}
}
