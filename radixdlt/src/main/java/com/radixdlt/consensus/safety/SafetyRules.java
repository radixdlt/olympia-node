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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.Signature;

import java.util.Objects;

/**
 * Manages safety of the protocol.
 * TODO: Add storage of private key of node here
 */
public final class SafetyRules {
	private final EUID self;
	private final ECKeyPair key;
	private final VertexHasher hasher;

	private SafetyState state;

	@Inject
	public SafetyRules(@Named("self") EUID self, @Named("self") ECKeyPair key, VertexHasher hasher, SafetyState initialState) {
		this.self = Objects.requireNonNull(self);
		this.key = Objects.requireNonNull(key);
		this.hasher = Objects.requireNonNull(hasher);
		this.state = new SafetyState(initialState.lastVotedRound, initialState.lockedRound);
	}

	@VisibleForTesting
	AID getCommittedAtom(Vertex vertex) {
		if (vertex.getRound().equals(vertex.getQC().getRound().next())
			&& vertex.getQC().getRound().equals(vertex.getQC().getParentRound().next())) {
			return vertex.getQC().getVertexMetadata().getParentAID();
		}
		return null;
	}

	/**
	 * Process a quorum certificate
	 * @param qc The quorum certificate
	 */
	public void process(QuorumCertificate qc) {
		if (qc.getParentRound().compareTo(this.state.lockedRound) > 0) {
			this.state = this.state.withLockedRound(qc.getParentRound());
		}
	}

	/**
	 * Vote for a proposed vertex while ensuring that safety invariants are upheld.
	 * @param proposedVertex The proposed vertex
	 * @return A vote result containing the vote and any committed vertices
	 * @throws SafetyViolationException In case the vertex would violate a safety invariant
	 */
	public VoteResult voteFor(Vertex proposedVertex) throws SafetyViolationException, CryptoException {
		// ensure vertex does not violate earlier rounds
		if (proposedVertex.getRound().compareTo(this.state.lastVotedRound) <= 0) {
			throw new SafetyViolationException(proposedVertex, this.state, String.format(
				"violates earlier vote at %s", this.state.lastVotedRound));
		}

		// ensure vertex respects locked QC
		if (proposedVertex.getQC().getRound().compareTo(this.state.lockedRound) < 0) {
			throw new SafetyViolationException(proposedVertex, this.state, String.format(
				"does not respect locked round %s", this.state.lockedRound));
		}

		this.state = this.state.withLastVotedRound(proposedVertex.getRound());
		VertexMetadata vertexMetadata = new VertexMetadata(
			proposedVertex.getRound(),
			proposedVertex.getAID(),
			proposedVertex.getQC().getVertexMetadata().getRound(),
			proposedVertex.getQC().getVertexMetadata().getAID()
		);
		Hash vertexHash = this.hasher.hash(vertexMetadata);
		Signature signature = this.key.sign(vertexHash);
		Vote vote = new Vote(self, vertexMetadata, signature);
		AID committedAtom = getCommittedAtom(proposedVertex);

		return new VoteResult(vote, committedAtom);
	}

	@VisibleForTesting SafetyState getState() {
		return state;
	}
}
