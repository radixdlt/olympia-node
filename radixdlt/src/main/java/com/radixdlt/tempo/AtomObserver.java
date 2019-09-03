package com.radixdlt.tempo;

import com.radixdlt.common.AID;

/**
 * Observes atom events.
 */
public interface AtomObserver {
	/**
	 * Called whenever a new non-conflicting atom is adopted (after it has been stored).
	 * @param atom The adopted atom
	 */
	default void onAdopted(TempoAtom atom) {
	}

	/**
	 * Called whenever a previously adopted atom is deleted (after it has been deleted).
	 * @param aid The aid of the deleted atom
	 */
	default void onDeleted(AID aid) {
	}
}
