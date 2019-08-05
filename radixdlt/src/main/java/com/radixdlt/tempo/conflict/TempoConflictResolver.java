package com.radixdlt.tempo.conflict;

import com.radixdlt.Atom;
import com.radixdlt.tempo.ConflictResolver;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TempoConflictResolver implements ConflictResolver {
	@Override
	public CompletableFuture<Atom> resolve(Set<Atom> conflictingAtoms) {
		throw new UnsupportedOperationException();
	}
}
