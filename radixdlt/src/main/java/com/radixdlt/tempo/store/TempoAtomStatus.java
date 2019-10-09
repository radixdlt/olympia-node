package com.radixdlt.tempo.store;

/**
 * Status of an {@link com.radixdlt.Atom} in Tempo consensus
 */
public enum TempoAtomStatus {
	/**
	 * The atom is unavailable.
	 */
	UNAVAILABLE,

	/**
	 * The atom is available, pending but not yet committed.
	 */
	PENDING,

	/**
	 * The atom is available and irreversibly committed.
	 */
	COMMITTED
}
