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
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.safety.SafetyState.Builder;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages safety of the protocol.
 */
public final class SafetyRules {
	private final ECKeyPair selfKey; // TODO remove signing/address to separate identity management

	private SafetyState state;

	@Inject
	public SafetyRules(@Named("self") ECKeyPair selfKey, SafetyState initialState) {
		this.selfKey = Objects.requireNonNull(selfKey);
		this.state = Objects.requireNonNull(initialState);
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
		final Builder safetyStateBuilder = this.state.toBuilder();

		// pre-commit phase on vertex's parent if there is a newer consecutive 1-chain
		// keep highest 1-chain as the current "generic" QC
		if (qc.getView().compareTo(this.state.getGenericView().orElse(View.of(0L))) > 0) {
			safetyStateBuilder.qc(qc);
		}

		if (qc.getParent() != null
			&& qc.getParent().getView().compareTo(this.state.getLockedView()) > 0
			&& qc.getParent().getView().next().equals(qc.getView())) {

			safetyStateBuilder.lockedView(qc.getParent().getView());
		}

		final Optional<Hash> commitHash;
		if (qc.getCommitted().isPresent()) {
			VertexMetadata committed = qc.getCommitted().get();
			if (committed.getView().compareTo(this.state.getCommittedView()) > 0) {
				safetyStateBuilder.committedView(committed.getView());
				commitHash = Optional.of(committed.getId());
			} else {
				commitHash = Optional.empty();
			}
		} else {
			commitHash = Optional.empty();
		}

		this.state = safetyStateBuilder.build();

		return commitHash;
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

		Builder safetyStateBuilder = this.state.toBuilder();
		safetyStateBuilder.lastVotedView(proposedVertex.getView());

		final VertexMetadata proposed = VertexMetadata.ofVertex(proposedVertex);
		final VertexMetadata parent = VertexMetadata.ofParent(proposedVertex);
		final VertexMetadata committed;

		if (proposedVertex.getView().equals(proposedVertex.getParentView().next())
			&& !proposedVertex.getParentView().isGenesis() && !proposedVertex.getGrandParentView().isGenesis()
			&& proposedVertex.getParentView().equals(proposedVertex.getGrandParentView().next())
		) {
			committed = proposedVertex.getQC().getParent();
		} else {
			committed = null;
		}

		final VoteData voteData = new VoteData(proposed, parent, committed);

		Hash voteHash;
		try {
			voteHash = Hash.of(DefaultSerialization.getInstance().toDson(voteData, Output.HASH));
		} catch (SerializationException e) {
			throw new IllegalStateException("Failed to serialize for hash.");
		}

		this.state = safetyStateBuilder.build();

		// TODO make signing more robust by including author in signed hash
		ECDSASignature signature = this.selfKey.sign(voteHash);
		return new Vote(selfKey.getPublicKey(), voteData, signature);
	}

	@VisibleForTesting SafetyState getState() {
		return state;
	}
}
