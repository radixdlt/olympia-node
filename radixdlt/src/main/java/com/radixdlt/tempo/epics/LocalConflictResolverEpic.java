package com.radixdlt.tempo.epics;

import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.OnConflictResolvedAction;
import com.radixdlt.tempo.actions.ResolveConflictAction;

import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Local conflict resolver which
 */
public class LocalConflictResolverEpic implements TempoEpic {
	private final EUID self;

	public LocalConflictResolverEpic(EUID self) {
		this.self = self;
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (action instanceof ResolveConflictAction) {
			ResolveConflictAction resolve = (ResolveConflictAction) action;
			TempoAtom winner = resolve.allAtoms()
				.filter(a -> a.getTemporalProof().hasVertexByNID(self))
				.map(a -> Pair.of(a.getTemporalProof().getVertexByNID(self).getClock(), a))
				.min(Comparator.comparingLong(Pair::getFirst))
				.map(Pair::getSecond)
				.orElseThrow(() -> new TempoException("Error while resolving conflict, no atom has vertex by self"));
			// TODO get rid of ugly hack to get conflict winner back to Ledger interface
			resolve.getWinnerFuture().complete(winner);
			return Stream.of(new OnConflictResolvedAction(winner, resolve.allAtoms().collect(Collectors.toSet())));
		}

		return Stream.empty();
	}
}
