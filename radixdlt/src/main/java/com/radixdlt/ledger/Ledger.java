package com.radixdlt.ledger;

import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor.Type;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * An instance of a ledger which may be synchronised across a set of nodes.
 */
public interface Ledger {
	/**
	 * Receives a new atom, blocking until an atom becomes available.
	 *
	 * @return The received atom
	 */
	Atom receive() throws InterruptedException;

	/**
	 * Gets the atom associated with a certain {@link AID}.
	 *
	 * @param aid The {@link AID}
	 * @return The atom associated with the given {@link AID}
	 */
	Optional<Atom> get(AID aid);

	/**
	 * Stores an {@link Atom} with certain indices.
	 *
	 * @param atom The atom
	 * @param uniqueIndices The unique indices
	 * @param duplicateIndices The duplicate indices
	 * @return Whether the {@link Atom} was stored
	 */
	boolean store(Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

	/**
	 * Deletes the atom associated with a certain {@link AID}.
	 *
	 * @param aid The {@link AID}
	 * @return Whether the {@link AID} was deleted
	 */
	boolean delete(AID aid);

	/**
	 * Replaces a set of atoms with another atom in an atomic operation
	 * @param aids The aids to delete
	 * @param atom The new atom
	 * @param uniqueIndices The unique indices of that atom
	 * @param duplicateIndices The duplicate indices of that atom
	 * @return Whether all {@link AID}s were successfully deleted
	 */
	boolean replace(Set<AID> aids, Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

	/**
	 * Searches for a certain index.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	LedgerCursor search(Type type, LedgerIndex index, LedgerSearchMode mode);

	/**
	 * Resolves a conflict between a non-empty set of atoms, returns the winning atom.
	 *
	 * @param conflictingAtoms The non-empty set of conflicting atoms
	 * @return a {@link Future} yielding the winning atom
	 */
	Future<Atom> resolve(Set<Atom> conflictingAtoms);
}
