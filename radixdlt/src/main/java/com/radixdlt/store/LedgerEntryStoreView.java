package com.radixdlt.store;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;

import java.util.Optional;
import java.util.Set;

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
	 * Gets the status for a certain aid.
	 * @param aid The aid
	 * @return The status
	 */
	LedgerEntryStatus getStatus(AID aid);

	/**
	 * Gets the pending aids
	 * @return The pending aids
	 */
	Set<AID> getPending();

	/**
	 * Gets the atom associated with a certain aid
	 * @param aid The aid
	 * @return The atom associated with the given aid (if any)
	 */
	Optional<LedgerEntry> get(AID aid);

	/**
	 * Gets the unique indices associated with a certain aid
	 * @param aid The aid
	 * @return The unique indices
	 */
	Set<StoreIndex> getUniqueIndices(AID aid);

	/**
	 * Searches for a certain index.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	SearchCursor search(StoreIndex.LedgerIndexType type, StoreIndex index, LedgerSearchMode mode);

	/**
	 * Checks whether a certain index is contained in this ledger.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	boolean contains(StoreIndex.LedgerIndexType type, StoreIndex index, LedgerSearchMode mode);

	/**
	 * Advance the cursor to discover up to certain number of aids within a shard range
	 * @param logicalClock The current cursor
	 * @param limit The maximum number of aids
	 * @return The relevant aids and the advanced cursor
	 */
	ImmutableList<AID> getNextCommitted(long logicalClock, int limit);
}
