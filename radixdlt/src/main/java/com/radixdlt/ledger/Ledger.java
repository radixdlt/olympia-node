package com.radixdlt.ledger;

import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor.Type;
import com.radixdlt.ledger.exceptions.LedgerException;
import com.radixdlt.ledger.exceptions.LedgerKeyConstraintException;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * An instance of a ledger which may be synchronised across a set of nodes.
 */
public interface Ledger {
	/**
	 * Receives a new atom, blocking until an atom becomes available.
	 *
	 * @return The received atom
	 *
	 * @throws LedgerException in case of internal errors
	 */
	Atom receive() throws InterruptedException;

	/**
	 * Gets the atom associated with a certain {@link AID}.
	 *
	 * @param aid The {@link AID}
	 * @return The atom associated with the given {@link AID}
	 *
	 * @throws LedgerException in case of internal errors
	 */
	Optional<Atom> get(AID aid);

	/**
	 * Stores an {@link Atom} with certain indices.
	 *
	 * @param atom The atom
	 * @param uniqueIndices The unique indices
	 * @param duplicateIndices The duplicate indices
	 * @return Whether the {@link Atom} was stored
	 *
	 * @throws LedgerKeyConstraintException if unique key constraints were violated
	 * @throws LedgerException in case of internal errors
	 */
	boolean store(Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

	/**
	 * Deletes the atom associated with a certain {@link AID}.
	 *
	 * @param aid The {@link AID}
	 * @return Whether the {@link AID} was deleted
	 *
	 * @throws LedgerException in case of internal errors
	 */
	boolean delete(AID aid);

	/**
	 * Replaces a set of atoms with another atom in an atomic operation
	 * @param aids The aids to delete
	 * @param atom The new atom
	 * @param uniqueIndices The unique indices of that atom
	 * @param duplicateIndices The duplicate indices of that atom
	 * @return Whether all {@link AID}s were successfully deleted
	 *
	 * @throws LedgerKeyConstraintException if unique key constraints were violated
	 * @throws LedgerException in case of internal errors
	 */
	boolean replace(Set<AID> aids, Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

	/**
	 * Searches for a certain index.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 *
	 * @throws LedgerException in case of internal errors
	 */
	LedgerCursor search(Type type, LedgerIndex index, LedgerSearchMode mode);

	/**
	 * Resolves a conflict between an atom and non-empty set of conflicting atoms.
	 *
	 * @param atom The atom
	 * @param conflictingAtoms The non-empty set of conflicting atoms
	 * @return a {@link Future} yielding the winning atom
	 *
	 * @throws LedgerException in case of internal errors
	 */
	CompletableFuture<Atom> resolve(Atom atom, Collection<Atom> conflictingAtoms);
}
