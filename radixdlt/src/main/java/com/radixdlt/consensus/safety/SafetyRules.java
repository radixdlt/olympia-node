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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VoteMessage;

import java.util.Objects;

/**
 * Manages safety of the protocol.
 * TODO: Add storage of private key of node here
 */
public final class SafetyRules {
	private final EUID self;
	private final SafetyState state;

	@Inject
	public SafetyRules(@Named("self") EUID self, SafetyState initialState) {
		this.self = Objects.requireNonNull(self);
		this.state = new SafetyState(initialState.lastVotedRound, initialState.preferredRound);
	}

	private AID getCommittedAtom(Vertex vertex) {
		if (vertex.getRound().equals(vertex.getQC().getRound().next())
			&& vertex.getQC().getRound().equals(vertex.getQC().getParentRound().next())) {
			return vertex.getQC().getVertexMetadata().getParentAID();
		}
		return null;
	}

	public void process(QuorumCertificate qc) {
		if (qc.getParentRound().compareTo(this.state.preferredRound) > 0) {
			this.state.preferredRound = qc.getParentRound();
		}
	}

	public VoteResult vote(Vertex proposedVertex) throws SafetyViolationException {
		// ensure vertex does not violate earlier rounds
		if (proposedVertex.getRound().compareTo(this.state.lastVotedRound) < 0) {
			throw new SafetyViolationException(String.format(
				"Proposed vertex at %s would violate earlier vote at %s",
				proposedVertex.getRound(), this.state.lastVotedRound));
		}

		// ensure vertex respects preference
		if (proposedVertex.getQC().getRound().compareTo(this.state.preferredRound) < 0) {
			throw new SafetyViolationException(String.format(
				"Proposed vertex QC at %s does not respect preferred round %s",
				proposedVertex.getQC().getRound(), this.state.preferredRound));
		}

		this.state.lastVotedRound = proposedVertex.getRound();
		VertexMetadata vertexMetadata = new VertexMetadata(
			proposedVertex.getRound(),
			proposedVertex.getAID(),
			proposedVertex.getQC().getVertexMetadata().getRound(),
			proposedVertex.getQC().getVertexMetadata().getAID()
		);
		VoteMessage vote = new VoteMessage(self, vertexMetadata);
		AID committedAtom = getCommittedAtom(proposedVertex);

		return new VoteResult(vote, committedAtom);
	}

}
