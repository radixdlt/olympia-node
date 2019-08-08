package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.common.Pair;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.tempo.sync.IterativeCursor;
import org.radix.shards.ShardSpace;

import java.util.Optional;

/**
 * A read-only view of a specific AtomStore
 */
public interface AtomStoreView {
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
	Optional<TempoAtom> get(AID aid);

	/**
	 * Searches for a certain index.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	LedgerCursor search(LedgerCursor.Type type, LedgerIndex index, LedgerSearchMode mode);

	/**
	 * Advance the cursor to discover up to certain number of aids within a shard range
	 * @param cursor The current cursor
	 * @param limit The maximum number of aids
	 * @param shardSpace The shard range to consider
	 * @return The relevant aids and the advanced cursor
	 */
	Pair<ImmutableList<AID>, IterativeCursor> getNext(IterativeCursor cursor, int limit, ShardSpace shardSpace);
}
