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
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.AID;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;

import java.util.Objects;

/**
 * Manages safety of the protocol.
 * TODO: Add storage of private key of node here
 */
public final class SafetyRules {
	private final RadixAddress selfAddress;
	private final ECKeyPair selfKey;
	private final VertexHasher hasher;

	private SafetyState state;

	@Inject
	public SafetyRules(@Named("self") RadixAddress selfAddress,
	                   @Named("self") ECKeyPair selfKey,
	                   VertexHasher hasher,
	                   SafetyState initialState) {
		this.selfAddress = Objects.requireNonNull(selfAddress);
		this.selfKey = Objects.requireNonNull(selfKey);
		if (!selfAddress.getKey().equals(selfKey.getPublicKey())) {
			throw new IllegalArgumentException("Address and key mismatch: " + selfAddress + " != " + selfKey);
		}
		this.hasher = Objects.requireNonNull(hasher);
		this.state = new SafetyState(initialState.lastVotedView, initialState.lockedView);
	}

	@VisibleForTesting
	AID getCommittedAtom(Vertex vertex) {
		if (vertex.getView().equals(vertex.getQC().getView().next())
			&& vertex.getQC().getView().equals(vertex.getQC().getParentView().next())) {
			return vertex.getQC().getVertexMetadata().getParentAID();
		}
		return null;
	}

	/**
	 * Process a quorum certificate
	 * @param qc The quorum certificate
	 */
	public void process(QuorumCertificate qc) {
		if (qc.getParentView().compareTo(this.state.lockedView) > 0) {
			this.state = this.state.withLockedView(qc.getParentView());
		}
	}

	/**
	 * Vote for a proposed vertex while ensuring that safety invariants are upheld.
	 * @param proposedVertex The proposed vertex
	 * @return A vote result containing the vote and any committed vertices
	 * @throws SafetyViolationException In case the vertex would violate a safety invariant
	 */
	public VoteResult voteFor(Vertex proposedVertex) throws SafetyViolationException, CryptoException {
		// ensure vertex does not violate earlier votes
		if (proposedVertex.getView().compareTo(this.state.lastVotedView) <= 0) {
			throw new SafetyViolationException(proposedVertex, this.state, String.format(
				"violates earlier vote at %s", this.state.lastVotedView));
		}

		// ensure vertex respects locked QC
		if (proposedVertex.getQC().getView().compareTo(this.state.lockedView) < 0) {
			throw new SafetyViolationException(proposedVertex, this.state, String.format(
				"does not respect locked view %s", this.state.lockedView));
		}

		this.state = this.state.withLastVotedView(proposedVertex.getView());
		VertexMetadata vertexMetadata = new VertexMetadata(
			proposedVertex.getView(),
			proposedVertex.getAID(),
			proposedVertex.getQC().getVertexMetadata().getView(),
			proposedVertex.getQC().getVertexMetadata().getAID()
		);
		Hash vertexHash = this.hasher.hash(vertexMetadata);
		ECDSASignature signature = this.selfKey.sign(vertexHash);
		Vote vote = new Vote(selfAddress, vertexMetadata, signature);
		AID committedAtom = getCommittedAtom(proposedVertex);

		return new VoteResult(vote, committedAtom);
	}

	@VisibleForTesting SafetyState getState() {
		return state;
	}
}
