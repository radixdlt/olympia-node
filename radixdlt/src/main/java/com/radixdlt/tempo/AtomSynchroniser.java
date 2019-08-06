package com.radixdlt.tempo;

import com.radixdlt.common.EUID;

import java.util.List;

/**
 * A mechanism for synchronising atoms.
 */
public interface AtomSynchroniser {
	/**
	 * Receives a new inbound atom from a queue, blocking until an atom becomes available.
	 *
	 * @return The received atom
	 */
	TempoAtom receive() throws InterruptedException;

	/**
	 * Clears the inbound atom queue.
	 */
	void clear();

	/**
	 * Selects the next edges for a given atom.
	 * @param atom The atom
	 * @return An ordered list of next edges for the atom
	 */
	List<EUID> selectEdges(TempoAtom atom);

	/**
	 * Synchronises a new atom.
	 * @param atom The atom to synchronise
	 */
	void synchronise(TempoAtom atom);

	/**
	 * Get a legacy view of this synchroniser
	 * TODO remove this once AtomSync is removed
	 */
	AtomSyncView getLegacyAdapter();
}
