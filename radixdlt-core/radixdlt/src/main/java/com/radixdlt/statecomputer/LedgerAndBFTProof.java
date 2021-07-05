/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.statecomputer;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;

import java.util.Objects;
import java.util.Optional;

/**
 * Proof of ledger commit
 */
public final class LedgerAndBFTProof {
	private final LedgerProof ledgerProof;
	private final VerifiedVertexStoreState vertexStoreState;
	private final Optional<HashCode> nextForkHash;

	private LedgerAndBFTProof(
		LedgerProof ledgerProof,
		VerifiedVertexStoreState vertexStoreState,
		Optional<HashCode> nextForkHash
	) {
		this.ledgerProof = ledgerProof;
		this.vertexStoreState = vertexStoreState;
		this.nextForkHash = nextForkHash;
	}

	public static LedgerAndBFTProof create(LedgerProof ledgerProof) {
		return create(ledgerProof, null);
	}

	public static LedgerAndBFTProof create(LedgerProof ledgerProof, VerifiedVertexStoreState vertexStoreState) {
		return create(ledgerProof, vertexStoreState, Optional.empty());
	}

	public static LedgerAndBFTProof create(LedgerProof ledgerProof, VerifiedVertexStoreState vertexStoreState, Optional<HashCode> nextForkHash) {
		Objects.requireNonNull(ledgerProof);
		return new LedgerAndBFTProof(ledgerProof, vertexStoreState, nextForkHash);
	}

	public LedgerProof getProof() {
		return ledgerProof;
	}

	public Optional<VerifiedVertexStoreState> vertexStoreState() {
		return Optional.ofNullable(vertexStoreState);
	}

	public Optional<HashCode> getNextForkHash() {
		return nextForkHash;
	}

	public LedgerAndBFTProof withNextForkHash(HashCode nextForkHash) {
		return new LedgerAndBFTProof(ledgerProof, vertexStoreState, Optional.of(nextForkHash));
	}

	@Override
	public int hashCode() {
		return Objects.hash(ledgerProof, vertexStoreState, nextForkHash);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LedgerAndBFTProof)) {
			return false;
		}

		var other = (LedgerAndBFTProof) o;
		return Objects.equals(this.ledgerProof, other.ledgerProof)
			&& Objects.equals(this.vertexStoreState, other.vertexStoreState)
			&& Objects.equals(this.nextForkHash, other.nextForkHash);
	}
}
