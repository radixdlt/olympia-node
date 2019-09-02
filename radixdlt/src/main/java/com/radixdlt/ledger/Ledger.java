package com.radixdlt.ledger;

import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.exceptions.LedgerException;
import com.radixdlt.ledger.exceptions.LedgerIndexConflictException;

import java.util.Optional;
import java.util.Set;

/**
 * An instance of a ledger which may be synchronised across a set of nodes.
 */
public interface Ledger {
	/**
	 * Receives observations of this ledger, blocking until an atom becomes available.
	 *
	 * @return The atom observation
	 *
	 * @throws LedgerException in case of internal errors
	 */
	AtomObservation observe() throws InterruptedException;

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
	 * Submits an {@link Atom} with certain indices.
	 *
	 * @param atom The atom
	 * @param uniqueIndices The unique indices
	 * @param duplicateIndices The duplicate indices
	 *
	 * @throws LedgerIndexConflictException if the unique indices conflict with existing indices
	 * @throws LedgerException in case of internal errors
	 */
	void store(Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

	/**
	 * Replaces a set of atoms with another atom in an atomic operation
	 * @param aids The aids to delete
	 * @param atom The new atom
	 * @param uniqueIndices The unique indices of that atom
	 * @param duplicateIndices The duplicate indices of that atom
	 *
	 * @throws LedgerIndexConflictException if the unique indices conflict with existing indices
	 * @throws LedgerException in case of internal errors
	 */
	void replace(Set<AID> aids, Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

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
	LedgerCursor search(LedgerIndex.LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode);

	/**
	 * Checks whether a certain index is contained in this ledger.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 *
	 * @throws LedgerException in case of internal errors
	 */
	boolean contains(LedgerIndex.LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode);
}
