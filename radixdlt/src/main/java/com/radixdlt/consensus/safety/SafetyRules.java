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
	private final ECKeyPair selfKey; // TODO remove signing/address to separate identity management

	private final VertexStore vertexStore;
	private SafetyState state;

	@Inject
	public SafetyRules(@Named("self") ECKeyPair selfKey,
	                   VertexStore vertexStore,
	                   SafetyState initialState) {
		this.selfKey = Objects.requireNonNull(selfKey);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.state = new SafetyState(initialState);
	}

	/**
	 * Process a vertex
	 * @param vertex The vertex
	 * @return the just-committed vertex id, if any
	 */
	public Optional<Hash> process(Vertex vertex) {
		return process(vertex.getQC());
	}

	/**
	 * Process a QC.
	 * @param qc The quorum certificate
	 * @return the just-committed vertex id, if any
	 */
	public Optional<Hash> process(QuorumCertificate qc) {
		// pre-commit phase on vertex's parent if there is a newer consecutive 1-chain
		// keep highest 1-chain as the current "generic" QC
		if (qc.getView().compareTo(this.state.getGenericView().orElse(View.of(0L))) > 0) {
			this.state = this.state.withGenericQC(qc);
		}

		// commit phase on vertex's grandparent if there is a newer consecutive 2-chain
		// keep the highest consecutive 2-chain as the locked QC
		Vertex parent = vertexStore.getVertex(qc.getVertexMetadata().getId());
		if (parent == null) {
			throw new IllegalStateException(String.format(
				"QC %s has no vertex at %s", qc, qc.getVertexMetadata().getId()));
		}
		// do not go beyond genesis
		if (!parent.isGenesis()) {
			boolean twoChain = parent.getQC().getView().next().equals(parent.getView());
			if (twoChain && parent.getQC().getView().compareTo(this.state.getLockedView()) > 0) {
				this.state = this.state.withLockedView(parent.getQC().getView());
			}

			// decide phase on vertex's great-grandparent if there is a newer consecutive 3-chain
			// return committed aid
			Vertex grandparent = vertexStore.getVertex(parent.getQC().getVertexMetadata().getId());
			if (grandparent == null) {
				throw new IllegalStateException(String.format(
					"QC %s has no vertex at %s", qc, qc.getVertexMetadata().getId()));
			}
			// do not go beyond genesis
			if (!grandparent.isGenesis() && twoChain) {
				boolean threeChain = qc.getVertexMetadata().getId().equals(parent.getId())
					&& parent.getQC().getVertexMetadata().getId().equals(grandparent.getId())
					&& grandparent.getQC().getView().next().equals(grandparent.getView());
				if (threeChain && grandparent.getQC().getView().compareTo(this.state.getCommittedView()) > 0) {
					this.state = this.state.withCommittedView(grandparent.getQC().getView());
					return Optional.of(grandparent.getQC().getVertexMetadata().getId());
				}
			}
		}

		return Optional.empty();
	}

	/**
	 * Vote for a proposed vertex while ensuring that safety invariants are upheld.
	 * @param proposedVertex The proposed vertex
	 * @return A vote result containing the vote and any committed vertices
	 * @throws SafetyViolationException In case the vertex would violate a safety invariant
	 */
	public Vote voteFor(Vertex proposedVertex) throws SafetyViolationException {
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
			proposedVertex.getId(),
			proposedVertex.getQC().getView(),
			proposedVertex.getQC().getVertexMetadata().getId()
		);
		try {
			// TODO make signing more robust by including author in signed hash
			ECDSASignature signature = this.selfKey.sign(proposedVertex.getId());
			return new Vote(selfKey.getPublicKey(), vertexMetadata, signature);
		} catch (CryptoException e) {
			throw new IllegalStateException("Failed to sign proposed vertex " + proposedVertex, e);
		}
	}

	@VisibleForTesting SafetyState getState() {
		return state;
	}
}
