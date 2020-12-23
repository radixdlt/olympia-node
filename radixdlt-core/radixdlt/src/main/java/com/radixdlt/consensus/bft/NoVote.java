/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import java.util.Objects;

/**
 * An event emitted when the node decides not to vote for a view
 */
public final class NoVote {
	private final VerifiedVertex vertex;

	private NoVote(VerifiedVertex vertex) {
		this.vertex = vertex;
	}

	public static NoVote create(VerifiedVertex vertex) {
		return new NoVote(vertex);
	}

	public VerifiedVertex getVertex() {
		return vertex;
	}

	@Override
	public String toString() {
		return String.format("%s{vertex=%s}", this.getClass().getSimpleName(), this.vertex);
	}

	@Override
	public int hashCode() {
		return Objects.hash(vertex);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof NoVote)) {
			return false;
		}

		NoVote other = (NoVote) o;
		return Objects.equals(this.vertex, other.vertex);
	}
}
