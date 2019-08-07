package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.common.Pair;
import com.radixdlt.tempo.sync.IterativeCursor;
import org.radix.database.exceptions.DatabaseException;
import org.radix.shards.ShardRange;

import java.util.List;
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
	 * Advance the cursor to discover up to certain number of aids within a shard range
	 * @param cursor The current cursor
	 * @param limit The maximum number of aids
	 * @param shardRange The shard range to consider
	 * @return The relevant aids and the advanced cursor
	 */
	Pair<ImmutableList<AID>, IterativeCursor> getNext(IterativeCursor cursor, int limit, ShardRange shardRange);
}
