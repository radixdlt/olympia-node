package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;

import java.util.Set;

/**
 * Observes atom events.
 */
public interface AtomObserver {
	/**
	 * Called whenever a new non-conflicting atom is adopted (after it has been stored).
	 * @param atom The adopted atom
	 * @param uniqueIndices The unique indices of that atom
	 * @param duplicateIndices The duplicate indices of that atom
	 */
	default void onAdopted(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
	}

	/**
	 * Called whenever a previously adopted atom is deleted (after it has been deleted).
	 * @param aid The aid of the deleted atom
	 */
	default void onDeleted(AID aid) {
	}
}
