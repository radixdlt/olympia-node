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
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;

import java.util.Objects;
import java.util.Optional;

/**
 * Manages safety of the protocol.
 */
public final class SafetyRules {
	private final RadixAddress selfAddress;
	private final ECKeyPair selfKey; // TODO remove signing/address to separate identity management
	private final VertexHasher hasher;

	private final VertexStore vertexStore;
	private SafetyState state;

	@Inject
	public SafetyRules(@Named("self") RadixAddress selfAddress,
	                   @Named("self") ECKeyPair selfKey,
	                   VertexHasher hasher,
	                   VertexStore vertexStore,
	                   SafetyState initialState) {
		this.selfAddress = Objects.requireNonNull(selfAddress);
		this.selfKey = Objects.requireNonNull(selfKey);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		if (!selfAddress.getKey().equals(selfKey.getPublicKey())) {
			throw new IllegalArgumentException("Address and key mismatch: " + selfAddress + " != " + selfKey);
		}
		this.hasher = Objects.requireNonNull(hasher);
		this.state = new SafetyState(initialState);
	}

	private Optional<Vertex> getVertexAt(View view) {
		return Optional.ofNullable(vertexStore.getVertex(view));
	}

	/**
	 * Process a vertex
	 * @param vertex The vertex
	 * @return the now-committed aid, if any
	 */
	public Optional<AID> process(Vertex vertex) {
		return process(vertex.getQC(), vertex.getView());
	}

	/**
	 * Process a QC seen at a certain view
	 * @param qc The quorum certificate
	 * @param view The view at which it was seen
	 * @return the now-committed aid, if any
	 */
	public Optional<AID> process(QuorumCertificate qc, View view) {
		// pre-commit phase on vertex's parent if there is a 1-chain
		// keep highest 1-chain as the current "generic" QC
		boolean oneChain = qc.getView().next().equals(view);
		if (oneChain && qc.getView().compareTo(this.state.getGenericView().orElse(View.of(0L))) > 0) {
			this.state = this.state.withGenericQC(qc);
		}

		// commit phase on vertex's grandparent if there is a 2-chain
		// keep the highest 2-chain as the locked QC
		Vertex parent = vertexStore.getVertex(qc.getView());
		if (parent == null) {
			return Optional.empty();
		}
		boolean twoChain = oneChain && parent.getQC().getView().next().equals(qc.getView());
		if (twoChain && parent.getQC().getView().compareTo(this.state.getLockedView()) > 0) {
			this.state = this.state.withLockedView(parent.getQC().getView());
		}

		// decide phase on vertex's great-grandparent if there is a 3-chain
		// return committed aid
		Vertex grandparent = vertexStore.getVertex(parent.getQC().getView());
		if (grandparent == null) {
			return Optional.empty();
		}
		boolean threeChain = twoChain && grandparent.getQC().getView().next().equals(parent.getQC().getView());
		if (threeChain) {
			return Optional.of(grandparent.getQC().getVertexMetadata().getAID());
		}

		return Optional.empty();
	}

	/**
	 * Vote for a proposed vertex while ensuring that safety invariants are upheld.
	 * @param proposedVertex The proposed vertex
	 * @return A vote result containing the vote and any committed vertices
	 * @throws SafetyViolationException In case the vertex would violate a safety invariant
	 */
	public Vote voteFor(Vertex proposedVertex) throws SafetyViolationException, CryptoException {
		// ensure vertex does not violate earlier votes
		if (proposedVertex.getView().compareTo(this.state.getLastVotedView()) <= 0) {
			throw new SafetyViolationException(proposedVertex, this.state, String.format(
				"violates earlier vote at %s", this.state.getLastVotedView()));
		}

		// ensure vertex respects locked QC
		if (proposedVertex.getQC().getView().compareTo(this.state.getLockedView()) < 0) {
			throw new SafetyViolationException(proposedVertex, this.state, String.format(
				"does not respect locked view %s", this.state.getLockedView()));
		}

		this.state = this.state.withLastVotedView(proposedVertex.getView());
		VertexMetadata vertexMetadata = new VertexMetadata(
			proposedVertex.getView(),
			proposedVertex.getAID()
		);
		// TODO make signing more robust by including author in signed hash
		Hash vertexHash = this.hasher.hash(vertexMetadata);
		ECDSASignature signature = this.selfKey.sign(vertexHash);

		return new Vote(selfAddress, vertexMetadata, signature);
	}

	@VisibleForTesting SafetyState getState() {
		return state;
	}
}
