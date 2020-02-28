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

import com.radixdlt.consensus.Round;

import java.util.Objects;

/**
 * The state maintained to ensure the safety of the consensus system.
 */
final class SafetyState {
	Round lastVotedRound; // the last round this node voted on
	Round lockedRound; // the highest 2-chain head

	public SafetyState(Round lastVotedRound, Round lockedRound) {
		this.lastVotedRound = lastVotedRound;
		this.lockedRound = lockedRound;
	}

	public SafetyState(SafetyState other) {
		this(other.lastVotedRound, other.lockedRound);
	}

	public static SafetyState initialState() {
		return new SafetyState(Round.of(0L), Round.of(0L));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SafetyState that = (SafetyState) o;
		return lastVotedRound.equals(that.lastVotedRound) &&
			lockedRound.equals(that.lockedRound);
	}

	@Override
	public int hashCode() {
		return Objects.hash(lastVotedRound, lockedRound);
	}

	@Override
	public String toString() {
		return "SafetyState{" +
			"lastVotedRound=" + lastVotedRound +
			", lockedRound=" + lockedRound +
			'}';
	}
}
