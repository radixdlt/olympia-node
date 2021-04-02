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
 */

package com.radixdlt.statecomputer;

import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.ParsedTransaction;

import java.util.List;
import java.util.Objects;

/**
 * Event signifying that an atom was committed to ledger successfully
 */
public final class AtomsCommittedToLedger {
    private final List<Txn> txns;
    private final List<ParsedTransaction> parsedTxs;

    private AtomsCommittedToLedger(List<Txn> txns, List<ParsedTransaction> parsedTxs) {
        this.txns = txns;
        this.parsedTxs = parsedTxs;
    }

    public List<Txn> getTxns() {
        return txns;
    }

    public List<ParsedTransaction> getParsedTxs() {
        return parsedTxs;
    }

    public static AtomsCommittedToLedger create(List<Txn> txns, List<ParsedTransaction> parsedTxs) {
        Objects.requireNonNull(txns);
        Objects.requireNonNull(parsedTxs);
        return new AtomsCommittedToLedger(txns, parsedTxs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txns, parsedTxs);
    }

    @Override
	public boolean equals(Object o) {
        if (!(o instanceof AtomsCommittedToLedger)) {
            return false;
        }

        AtomsCommittedToLedger other = (AtomsCommittedToLedger) o;
        return Objects.equals(this.txns, other.txns)
            && Objects.equals(this.parsedTxs, other.parsedTxs);
    }
}
