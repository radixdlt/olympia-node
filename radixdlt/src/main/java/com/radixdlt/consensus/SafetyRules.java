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

package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;

import java.util.Objects;
import java.util.Optional;

/**
 * Manages safety of the protocol.
 * TODO: Add storage of private key of node here
 */
public final class SafetyRules {
	private final EUID self;

	@Inject
	public SafetyRules(@Named("self") EUID self) {
		this.self = Objects.requireNonNull(self);
	}

	private AID getCommittedAtom(Vertex vertex) {
		if (vertex.getRound().equals(vertex.getQC().getRound().next())
			&& vertex.getQC().getRound().equals(vertex.getQC().getParentRound().next())) {
			return vertex.getQC().getVertexMetadata().getParentAID();
		}
		return null;
	}

	public VoteResult vote(Vertex vertex) {
		VertexMetadata vertexMetadata = new VertexMetadata(
			vertex.getRound(),
			vertex.getAID(),
			vertex.getQC().getVertexMetadata().getRound(),
			vertex.getQC().getVertexMetadata().getAID()
		);
		VoteMessage vote = new VoteMessage(self, vertexMetadata);
		AID committedAtom = getCommittedAtom(vertex);

		return new VoteResult(vote, committedAtom);
	}

	public static class ConsensusState {
		private Round lastVotedRound;
		private Round preferredRound;

	}

	public static class VoteResult {
		private final VoteMessage vote;
		private final AID committedAtom; // may be null

		public VoteResult(VoteMessage vote, AID committedAtom) {
			this.vote = Objects.requireNonNull(vote);
			this.committedAtom = committedAtom; // may be null
		}

		public VoteMessage getVote() {
			return vote;
		}

		public Optional<AID> getCommittedAtom() {
			return Optional.ofNullable(committedAtom);
		}
	}
}
