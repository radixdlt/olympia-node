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

import com.google.inject.Inject;
import com.radixdlt.consensus.safety.QuorumRequirements;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.Hash;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages pending votes for various vertices
 */
public final class PendingVotes {
	private final QuorumRequirements quorumRequirements;
	private final HashMap<Hash, ECDSASignatures> pendingVotes = new HashMap<>();

	@Inject
	public PendingVotes(QuorumRequirements quorumRequirements) {
		this.quorumRequirements = Objects.requireNonNull(quorumRequirements);
	}

	/**
	 * Inserts a vote for a given vertex, attempting to form a quorum certificate for that vertex.
	 *
	 * The vote will only be inserted if permitted by the installed {@link QuorumRequirements}.
	 * Similarly, a QC will only be formed if permitted by the installed {@link QuorumRequirements}.
	 * @param vote The vote to be inserted
	 * @return The generated QC, if any
	 */
	public Optional<QuorumCertificate> insertVote(Vote vote) {
		Objects.requireNonNull(vote, "vote");

		Hash voteId = vote.getVertexMetadata().getId();
		ECDSASignature signature = vote.getSignature().orElseThrow(() -> new IllegalArgumentException("vote is missing signature"));
		ECDSASignatures signatures = pendingVotes.getOrDefault(voteId, new ECDSASignatures());

		// try to add the signature to form a QC if permitted by the requirements
		if (quorumRequirements.accepts(vote.getAuthor().getUID())) {
			// FIXME ugly cast to ECDSASignatures because we need a specific type
			signatures = (ECDSASignatures) signatures.concatenate(vote.getAuthor(), signature);
		} else {
			// there is no meaningful inaction here, so better let the caller know
			throw new IllegalArgumentException("vote " + vote + " was not accepted into QC");
		}

		// try to form a QC with the added signature according to the requirements
		if (signatures.count() >= quorumRequirements.numRequiredVotes()) {
			// if QC could be formed, remove pending and return formed QC
			pendingVotes.remove(voteId);
			QuorumCertificate qc = new QuorumCertificate(vote.getVertexMetadata(), signatures);
			return Optional.of(qc);
		} else {
			// if no QC could be formed, update pending and return nothing
			pendingVotes.put(voteId, signatures);
			return Optional.empty();
		}
	}
}
