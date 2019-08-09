package com.radixdlt.tempo.conflict;

import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.tempo.ConflictResolver;
import com.radixdlt.tempo.TempoAtom;
import org.radix.time.TemporalProof;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * TODO remove, temporarly "local" conflict resolver
 */
public class LocalConflictResolver implements ConflictResolver {
	private final EUID self;

	public LocalConflictResolver(EUID self) {
		this.self = self;
	}

	@Override
	public CompletableFuture<TempoAtom> resolve(TempoAtom atom, Set<TempoAtom> conflictingAtoms) {
		// FIXME might break if there is no vertex by self
		Optional<TempoAtom> conflictWinner = Stream.concat(Stream.of(atom), conflictingAtoms.stream())
			.map(a -> Pair.of(a.getTemporalProof().getVertexByNID(self).getClock(), a))
			.min(Comparator.comparingLong(Pair::getFirst))
			.map(Pair::getSecond);
		// must be at least one so it's fine
		return CompletableFuture.completedFuture(conflictWinner.get());
	}
}
