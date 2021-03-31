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

package com.radixdlt.ledger;

import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;

import java.util.List;
import java.util.Objects;

/**
 * Commands along with proof that they have been committed on ledger.
 */
public final class VerifiedTxnsAndProof {
	private final List<Txn> txns;
	private final LedgerProof proof;

	public VerifiedTxnsAndProof(
		List<Txn> txns,
		LedgerProof proof
	) {
		this.txns = Objects.requireNonNull(txns);
		this.proof = Objects.requireNonNull(proof);
	}

	public List<Txn> getTxns() {
		return txns;
	}

	public boolean contains(Txn txn) {
		return txns.contains(txn);
	}

	public LedgerProof getProof() {
		return proof;
	}

	@Override
	public int hashCode() {
		return Objects.hash(txns, proof);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedTxnsAndProof)) {
			return false;
		}

		VerifiedTxnsAndProof other = (VerifiedTxnsAndProof) o;
		return Objects.equals(this.txns, other.txns)
			&& Objects.equals(this.proof, other.proof);
	}

	@Override
	public String toString() {
		return String.format("%s{txns=%s proof=%s}", this.getClass().getSimpleName(), txns, proof);
	}
}
