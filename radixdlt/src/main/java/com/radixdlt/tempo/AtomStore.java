package com.radixdlt.tempo;

import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;

import java.util.Optional;
import java.util.Set;

/**
 * An entry-point for manipulating the state of a Tempo ledger.
 */
public interface AtomStore {
	/**
	 * Checks whether the given {@link AID} is contained in this store
	 * @param aid The {@link AID}
	 * @return Whether the given {@link AID} is contained in this store
	 */
	boolean contains(AID aid);

	/**
	 * Gets the atom associated with a certain {@link AID}.
	 *
	 * @param aid The {@link AID}
	 * @return The atom associated with the given {@link AID}
	 */
	Optional<TempoAtom> get(AID aid);

	/**
	 * Deletes the atom associated with a certain {@link AID}.
	 *
	 * @param aid The {@link AID}
	 * @return Whether the {@link AID} was deleted
	 */
	boolean delete(AID aid);

	/**
	 * Stores an {@link com.radixdlt.Atom} with certain indices.
	 *
	 * @param atom The atom
	 * @param uniqueIndices The unique indices
	 * @param duplicateIndices The duplicate indices
	 * @return Whether the {@link com.radixdlt.Atom} was stored
	 */
	boolean store(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

	/**
	 * Replaces a set of atoms with another atom in an atomic operation
	 * @param aids The aids to delete
	 * @param atom The new atom
	 * @param uniqueIndices The unique indices of that atom
	 * @param duplicateIndices The duplicate indices of that atom
	 * @return Whether all {@link AID}s were successfully deleted
	 */
	boolean replace(Set<AID> aids, TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

	/**
	 * Searches for a certain index.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	LedgerCursor search(LedgerCursor.Type type, LedgerIndex index, LedgerSearchMode mode);

	/**
	 * Get a read-only view of this atom store
	 * @return a view of this atom store
	 */
	AtomStoreView asReadOnlyView();
}
