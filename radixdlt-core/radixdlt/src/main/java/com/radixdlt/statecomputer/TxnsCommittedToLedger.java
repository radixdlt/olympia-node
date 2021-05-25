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

import com.radixdlt.constraintmachine.REParsedTxn;

import java.util.List;
import java.util.Objects;

/**
 * Event signifying that an atom was committed to ledger successfully
 */
public final class TxnsCommittedToLedger {
    private final List<REParsedTxn> parsedTxs;

    private TxnsCommittedToLedger(List<REParsedTxn> parsedTxs) {
        this.parsedTxs = parsedTxs;
    }

    public List<REParsedTxn> getParsedTxs() {
        return parsedTxs;
    }

    public static TxnsCommittedToLedger create(List<REParsedTxn> parsedTxs) {
        Objects.requireNonNull(parsedTxs);
        return new TxnsCommittedToLedger(parsedTxs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parsedTxs);
    }

    @Override
	public boolean equals(Object o) {
        if (!(o instanceof TxnsCommittedToLedger)) {
            return false;
        }

        TxnsCommittedToLedger other = (TxnsCommittedToLedger) o;
        return Objects.equals(this.parsedTxs, other.parsedTxs);
    }
}
