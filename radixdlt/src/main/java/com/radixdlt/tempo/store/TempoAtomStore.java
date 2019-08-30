package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.tempo.TempoAtom;

import java.util.Set;

/**
 * An entry-point for manipulating the state of a Tempo ledger.
 */
public interface TempoAtomStore extends TempoAtomStoreView, Store {
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
}
