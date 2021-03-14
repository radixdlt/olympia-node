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

import com.radixdlt.atommodel.Atom;
import com.radixdlt.utils.Pair;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Event describing atoms which have been removed from the mempool
 * after a commit.
 */
public final class AtomsRemovedFromMempool {
    private final List<Pair<Atom, Exception>> removed;

    private AtomsRemovedFromMempool(List<Pair<Atom, Exception>> removed) {
        this.removed = removed;
    }

    public static AtomsRemovedFromMempool create(List<Pair<Atom, Exception>> removed) {
        Objects.requireNonNull(removed);
        return new AtomsRemovedFromMempool(removed);
    }

    public void forEach(BiConsumer<Atom, Exception> biConsumer) {
        removed.forEach(p -> biConsumer.accept(p.getFirst(), p.getSecond()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(removed);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AtomsRemovedFromMempool)) {
            return false;
        }

        AtomsRemovedFromMempool other = (AtomsRemovedFromMempool) o;
        return Objects.equals(this.removed, other.removed);
    }
}
