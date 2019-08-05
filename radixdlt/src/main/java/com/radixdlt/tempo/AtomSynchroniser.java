package com.radixdlt.tempo;

import com.radixdlt.common.EUID;
import org.radix.atoms.Atom;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A mechanism for synchronising atoms.
 */
public interface AtomSynchroniser {
	/**
	 * Receives a new inbound atom from a queue, blocking until an atom becomes available
	 * @return The received atom
	 */
	Atom receive() throws InterruptedException;

	/**
	 * Clears the inbound atom queue.
	 */
	void clear();

	/**
	 * Selects the next edges for a given atom.
	 * @param atom The atom
	 * @return An ordered list of next edges for the atom
	 */
	List<EUID> selectEdges(Atom atom);

	/**
	 * Synchronises a new atom.
	 * @param atom The atom to synchronise
	 */
	void synchronise(Atom atom);
}
