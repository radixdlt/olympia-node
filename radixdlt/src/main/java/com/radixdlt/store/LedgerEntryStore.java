/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store;

import com.radixdlt.identifiers.AID;

import java.util.Set;

/**
 * A read/write instance of a ledger store containing ledger entries.
 */
public interface LedgerEntryStore extends LedgerEntryStoreView {
	/**
	 * Irreversibly commits this store to an atom with at a certain logical clock.
	 * Once committed, an atom may no longer be deleted or replaced.
	 */
	void commit(LedgerEntry atom, Set<StoreIndex> uniqueIndices, Set<StoreIndex> duplicateIndices);

	/**
	 * Irreversibly commits this store to an atom with at a certain logical clock.
	 * Once committed, an atom may no longer be deleted or replaced.
	 */
	void commit(AID aid);

	/**
	 * Stores a {@link LedgerEntry} with certain indices.
	 * The stored atom will be treated as 'pending' until it is eventually deleted or committed.
	 *
	 * @param ledgerEntry The ledgerEntry
	 * @param uniqueIndices The unique indices
	 * @param duplicateIndices The duplicate indices
	 * @return Whether the {@link LedgerEntry} was stored
	 */
	LedgerEntryStoreResult store(LedgerEntry ledgerEntry, Set<StoreIndex> uniqueIndices, Set<StoreIndex> duplicateIndices);

	/**
	 * Replaces a set of atoms with another atom in an atomic operation
	 * The stored atom will be treated as 'pending' until it is eventually deleted or committed.

	 * @param aids The aids to delete
	 * @param ledgerEntry The new ledgerEntry
	 * @param uniqueIndices The unique indices of that atom
	 * @param duplicateIndices The duplicate indices of that atom
	 * @return Whether all {@link AID}s were successfully deleted
	 */
	LedgerEntryStoreResult replace(Set<AID> aids, LedgerEntry ledgerEntry, Set<StoreIndex> uniqueIndices, Set<StoreIndex> duplicateIndices);

	void reset();

	void close();
}
