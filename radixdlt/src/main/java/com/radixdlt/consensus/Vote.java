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

import java.util.Objects;

/**
 * Represents a vote on a vertex
 */
public final class Vote {
	private final int hash;
	private final long round;

	/**
	 * Create a vote for a given round with a certain hash.
	 * Note that the hash must reflect the given round.
	 * This is a temporary method as Vote will be expanded to maintain this invariant itself.
	 */
	public Vote(long round, int hash) {
		this.round = round;
		this.hash = hash;
	}

	public long getRound() {
		return round;
	}

	@Override
	public int hashCode() {
		return Objects.hash(round, hash);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vote)) {
			return false;
		}

		Vote v = (Vote) o;
		return v.hash == this.hash && v.round == this.round;
	}

	@Override
	public String toString() {
		return "Vote{" +
			"hash=" + hash +
			", round=" + round +
			'}';
	}
}
