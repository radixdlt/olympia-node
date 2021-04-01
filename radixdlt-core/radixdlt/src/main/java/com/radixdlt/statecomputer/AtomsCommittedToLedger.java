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

import com.radixdlt.consensus.Command;
import com.radixdlt.constraintmachine.ParsedTransaction;

import java.util.List;
import java.util.Objects;

/**
 * Event signifying that an atom was committed to ledger successfully
 */
public final class AtomsCommittedToLedger {
    private final List<Command> atoms;
    private final List<ParsedTransaction> parsedTxs;

    private AtomsCommittedToLedger(List<Command> atoms, List<ParsedTransaction> parsedTxs) {
        this.atoms = atoms;
        this.parsedTxs = parsedTxs;
    }

    public List<Command> getAtoms() {
        return atoms;
    }

    public List<ParsedTransaction> getParsedTxs() {
        return parsedTxs;
    }

    public static AtomsCommittedToLedger create(List<Command> atoms, List<ParsedTransaction> parsedTxs) {
        Objects.requireNonNull(atoms);
        Objects.requireNonNull(parsedTxs);
        return new AtomsCommittedToLedger(atoms, parsedTxs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(atoms, parsedTxs);
    }

    @Override
	public boolean equals(Object o) {
        if (!(o instanceof AtomsCommittedToLedger)) {
            return false;
        }

        AtomsCommittedToLedger other = (AtomsCommittedToLedger) o;
        return Objects.equals(this.atoms, other.atoms)
            && Objects.equals(this.parsedTxs, other.parsedTxs);
    }
}
