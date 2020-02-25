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

import com.radixdlt.common.AID;

import java.util.Objects;
import java.util.Optional;

/**
 * Manages safety of the protocol.
 * TODO: Add storage of private key of node here
 */
public final class SafetyRules {
	public VoteResult vote(Vertex vertex) {
		Vote vote = new Vote(vertex.getRound(), vertex.hashCode());
		return new VoteResult(vote, null);
	}

	public static class ConsensusState {
		private Round lastVotedRound;
		private Round preferredRound;

	}

	public static class VoteResult {
		private final Vote vote;
		private final AID committedAtom; // may be null

		public VoteResult(Vote vote, AID committedAtom) {
			this.vote = Objects.requireNonNull(vote);
			this.committedAtom = committedAtom; // may be null
		}

		public Vote getVote() {
			return vote;
		}

		public Optional<AID> getCommittedAtom() {
			return Optional.ofNullable(committedAtom);
		}
	}
}
