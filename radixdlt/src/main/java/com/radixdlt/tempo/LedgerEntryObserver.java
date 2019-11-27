package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerEntry;

import java.util.Set;

/**
 * Observes atom events.
 */
public interface LedgerEntryObserver {
	/**
	 * Called whenever a new non-conflicting ledgerEntry is adopted (after it has been stored).
	 * @param ledgerEntry The adopted ledgerEntry
	 * @param uniqueIndices The unique indices of that ledgerEntry
	 * @param duplicateIndices The duplicate indices of that ledgerEntry
	 */
	default void onAdopted(LedgerEntry ledgerEntry, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
	}

	/**
	 * Called whenever a previously adopted atom is deleted (after it has been deleted).
	 * @param aid The aid of the deleted atom
	 */
	default void onDeleted(AID aid) {
	}
}
