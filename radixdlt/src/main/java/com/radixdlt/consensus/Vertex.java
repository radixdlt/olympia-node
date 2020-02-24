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

import com.radixdlt.common.Atom;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Vertex in the BFT Chain
 */
public final class Vertex {
	private final QuorumCertificate qc;
	private final Round round;
	private final Atom atom;
	private final Round parentRound;
	private final long parentId;

	public Vertex(QuorumCertificate qc, Round round, Atom atom, Round parentRound, long parentId) {
		this.qc = qc;
		this.atom = atom;
		this.round = Objects.requireNonNull(round);
		this.parentRound = parentRound;
		this.parentId = parentId;
	}

	public QuorumCertificate getQc() {
		return qc;
	}

	public Round getRound() {
		return round;
	}

	public Atom getAtom() {
		return atom;
	}

	public long getParentId() {
		return parentId;
	}

	public Round getParentRound() {
		return parentRound;
	}

	@Override
	public int hashCode() {
		return Objects.hash(qc, round, atom);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vertex)) {
			return false;
		}

		Vertex v = (Vertex) o;
		return Objects.equals(v.round, round)
			&& Objects.equals(v.atom, this.atom)
			&& Objects.equals(v.qc, this.qc);
	}
}
