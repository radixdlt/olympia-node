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

import java.util.Objects;

/**
 * An event which signifies that a command has been proposed but which
 * does not pass verification.
 */
public final class InvalidProposedTxn {
    private final Txn txn;
    private final Exception e;

    private InvalidProposedTxn(Txn txn, Exception e) {
        this.txn = txn;
        this.e = e;
    }

    public static InvalidProposedTxn create(Txn txn, Exception e) {
        Objects.requireNonNull(txn);
        Objects.requireNonNull(e);
        return new InvalidProposedTxn(txn, e);
    }

    @Override
    public String toString() {
        return String.format("%s{txn=%s ex=%s}", this.getClass().getSimpleName(), this.txn.getId(), this.e.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(txn, e);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InvalidProposedTxn)) {
            return false;
        }

        InvalidProposedTxn other = (InvalidProposedTxn) o;
        return Objects.equals(this.e, other.e)
            && Objects.equals(this.txn, other.txn);
    }
}
