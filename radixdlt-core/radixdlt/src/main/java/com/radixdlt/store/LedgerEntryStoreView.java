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

import com.google.common.collect.ImmutableList;
import com.radixdlt.identifiers.AID;

import java.util.Optional;

/**
 * A read-only view of a specific LedgerEntryStore
 */
public interface LedgerEntryStoreView {
	/**
	 * Checks whether the given aid is contained in this view
	 * @param aid The aid
	 * @return Whether the given aid is contained in this view
	 */
	boolean contains(AID aid);

	/**
	 * Gets the atom associated with a certain aid
	 * @param aid The aid
	 * @return The atom associated with the given aid (if any)
	 */
	Optional<LedgerEntry> get(AID aid);

	/**
	 * Gets the last committed atom aid
	 * @return The last committed atom aid
	 */
	Optional<AID> getLastCommitted();

	/**
	 * Searches for a certain index.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	SearchCursor search(StoreIndex.LedgerIndexType type, StoreIndex index);

	/**
	 * Checks whether a certain index is contained in this ledger.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @return The resulting ledger cursor
	 */
	boolean contains(Transaction tx, StoreIndex.LedgerIndexType type, StoreIndex index);

	/**
	 * Retrieve a chunk of {@link LedgerEntry} with state version greater than the given one
	 * in sequential order.
	 * @param stateVersion the state version to use as a search parameter
	 * @param limit the maximum count of ledger entries to return
	 * @return ledger entries satisfying the constraints
	 */
	ImmutableList<LedgerEntry> getNextCommittedLedgerEntries(long stateVersion, int limit) throws NextCommittedLimitReachedException;
}
