package com.radixdlt.tempo.store;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.exceptions.LedgerException;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;

import java.util.List;
import java.util.Optional;

/**
 * A read-only view of a specific AtomStore
 */
public interface TempoAtomStoreView {
	/**
	 * Checks whether the given aid is contained in this view
	 * @param aid The aid
	 * @return Whether the given aid is contained in this view
	 */
	boolean contains(AID aid);

	/**
	 * Gets the {@link AID} associated with a certain aid
	 * @param clock The clock
	 * @return The {@link AID} associated with the given clock (if any)
	 */
	Optional<AID> get(long clock);

	/**
	 * Gets the atom associated with a certain aid
	 * @param aid The aid
	 * @return The atom associated with the given aid (if any)
	 */
	Optional<TempoAtom> get(AID aid);

	/**
	 * Searches for a certain index.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	LedgerCursor search(LedgerIndex.LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode);

	/**
	 * Checks whether a certain index is contained in this ledger.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	boolean contains(LedgerIndex.LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode);

	/**
	 * Advance the cursor to discover up to certain number of aids within a shard range
	 * @param logicalClock The current cursor
	 * @param limit The maximum number of aids
	 * @return The relevant aids and the advanced cursor
	 */
	ImmutableList<AID> getNext(long logicalClock, int limit);
}
