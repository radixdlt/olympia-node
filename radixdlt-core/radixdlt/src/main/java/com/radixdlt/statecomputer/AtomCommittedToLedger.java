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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.EUID;

import java.util.Objects;

/**
 * Event signifying that an atom was committed to ledger successfully
 */
public final class AtomCommittedToLedger {
    private final CommittedAtom committedAtom;
    private final ImmutableSet<EUID> indices;

    private AtomCommittedToLedger(CommittedAtom committedAtom, ImmutableSet<EUID> indices) {
        this.committedAtom = committedAtom;
        this.indices = indices;
    }

    public CommittedAtom getAtom() {
        return committedAtom;
    }

    public ImmutableSet<EUID> getIndices() {
        return indices;
    }

    public static AtomCommittedToLedger create(CommittedAtom committedAtom) {
        Objects.requireNonNull(committedAtom);
        final ImmutableSet<EUID> destinations = committedAtom.getAtom()
            .upParticles()
            .flatMap(p -> p.getDestinations().stream())
            .collect(ImmutableSet.toImmutableSet());
        return new AtomCommittedToLedger(committedAtom, destinations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(committedAtom, indices);
    }

    @Override
	public boolean equals(Object o) {
        if (!(o instanceof AtomCommittedToLedger)) {
            return false;
        }

        AtomCommittedToLedger other = (AtomCommittedToLedger) o;
        return Objects.equals(this.committedAtom, other.committedAtom)
                && Objects.equals(this.indices, other.indices);
    }
}
