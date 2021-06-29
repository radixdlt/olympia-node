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

import com.google.common.collect.ClassToInstanceMap;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LedgerUpdate {
	private final VerifiedTxnsAndProof verifiedTxnsAndProof;
	// FIXME: Easiest way to implement this part for now
	private final ClassToInstanceMap<Object> output;

	public LedgerUpdate(VerifiedTxnsAndProof verifiedTxnsAndProof, ClassToInstanceMap<Object> output) {
		this.verifiedTxnsAndProof = Objects.requireNonNull(verifiedTxnsAndProof);
		this.output = output;
	}

	public ClassToInstanceMap<Object> getStateComputerOutput() {
		return output;
	}

	public List<Txn> getNewTxns() {
		return verifiedTxnsAndProof.getTxns();
	}

	public LedgerProof getTail() {
		return verifiedTxnsAndProof.getProof();
	}

	public Optional<BFTValidatorSet> getNextValidatorSet() {
		return verifiedTxnsAndProof.getProof().getNextValidatorSet();
	}

	@Override
	public String toString() {
		return String.format("%s{commands=%s}", this.getClass().getSimpleName(), verifiedTxnsAndProof);
	}

	@Override
	public int hashCode() {
		return Objects.hash(verifiedTxnsAndProof, output);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LedgerUpdate)) {
			return false;
		}

		LedgerUpdate other = (LedgerUpdate) o;
		return Objects.equals(other.verifiedTxnsAndProof, this.verifiedTxnsAndProof)
			&& Objects.equals(other.output, this.output);
	}
}
