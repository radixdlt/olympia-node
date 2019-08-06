package com.radixdlt.tempo;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * A conflict resolution mechanism.
 */
public interface ConflictResolver {
	/**
	 * Resolves a conflict between a non-empty set of atoms, returns the winning atom.
	 *
	 * @param atom
	 * @param conflictingAtoms The non-empty set of conflicting atoms
	 * @return a {@link Future} yielding the winning atom
	 */
	CompletableFuture<TempoAtom> resolve(TempoAtom atom, Set<TempoAtom> conflictingAtoms);
}
