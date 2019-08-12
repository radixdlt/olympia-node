package com.radixdlt.tempo;

import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.tempo.ConflictResolver;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.exceptions.TempoException;
import org.radix.time.TemporalProof;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Local conflict resolver which only
 */
public class LocalConflictResolver implements ConflictResolver {
	private final EUID self;

	public LocalConflictResolver(EUID self) {
		this.self = self;
	}

	@Override
	public void accept(TemporalProof temporalProof) {
		// nothing to do
	}

	@Override
	public CompletableFuture<TempoAtom> resolve(TempoAtom atom, Set<TempoAtom> conflictingAtoms) {
		Optional<TempoAtom> conflictWinner = Stream.concat(Stream.of(atom), conflictingAtoms.stream())
			.filter(a -> a.getTemporalProof().hasVertexByNID(self))
			.map(a -> Pair.of(a.getTemporalProof().getVertexByNID(self).getClock(), a))
			.min(Comparator.comparingLong(Pair::getFirst))
			.map(Pair::getSecond);
		// must be at least one so should be fine
		return CompletableFuture.completedFuture(conflictWinner.orElseThrow(() ->
			new TempoException("Error while resolving conflict, no atom has vertex by self")));
	}
}
