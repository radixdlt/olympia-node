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

import com.radixdlt.common.EUID;
import com.radixdlt.consensus.Vote;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of a successful vote by {@link SafetyRules}.
 */
public final class VoteResult {
	private final Vote vote;
	private final EUID committedVertexId; // may be null

	public VoteResult(Vote vote, EUID committedVertexId) {
		this.vote = Objects.requireNonNull(vote);
		this.committedVertexId = committedVertexId; // may be null
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		VoteResult that = (VoteResult) o;
		return Objects.equals(vote, that.vote)
			&& Objects.equals(committedVertexId, that.committedVertexId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(vote, committedVertexId);
	}

	public Vote getVote() {
		return vote;
	}

	public Optional<EUID> getCommittedVertexId() {
		return Optional.ofNullable(committedVertexId);
	}
}
