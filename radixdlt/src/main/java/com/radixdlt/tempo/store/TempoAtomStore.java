package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.tempo.Resource;
import com.radixdlt.tempo.TempoAtom;

import java.util.Set;

/**
 * An entry-point for manipulating the state of a Tempo ledger.
 */
public interface TempoAtomStore extends TempoAtomStoreView, Resource {
	/**
	 * Irreversibly commits this store to an atom with at a certain logical clock.
	 * Once committed, an atom may no longer be deleted or replaced.
	 *
	 * @param aid The aid
	 * @param logicalClock The logical clock
	 */
	void commit(AID aid, long logicalClock);

	/**
	 * Stores a {@link com.radixdlt.Atom} with certain indices.
	 * The stored atom will be treated as 'pending' until it is eventually deleted or committed.
	 *
	 * @param atom The atom
	 * @param uniqueIndices The unique indices
	 * @param duplicateIndices The duplicate indices
	 * @return Whether the {@link com.radixdlt.Atom} was stored
	 */
	AtomStoreResult store(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);

	/**
	 * Replaces a set of atoms with another atom in an atomic operation
	 * The stored atom will be treated as 'pending' until it is eventually deleted or committed.

	 * @param aids The aids to delete
	 * @param atom The new atom
	 * @param uniqueIndices The unique indices of that atom
	 * @param duplicateIndices The duplicate indices of that atom
	 * @return Whether all {@link AID}s were successfully deleted
	 */
	AtomStoreResult replace(Set<AID> aids, TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices);
}
