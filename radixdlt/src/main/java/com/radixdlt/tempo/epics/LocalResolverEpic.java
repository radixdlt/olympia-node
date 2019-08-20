package com.radixdlt.tempo.epics;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Pair;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.reactive.TempoEpic;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.actions.OnConflictResolvedAction;
import com.radixdlt.tempo.actions.RaiseConflictAction;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Local conflict resolver which
 */
public final class LocalResolverEpic implements TempoEpic {
	private final EUID self;

	public LocalResolverEpic(EUID self) {
		this.self = self;
	}

	@Override
	public Stream<TempoFlow<TempoAction>> epic(TempoFlowSource flow) {
		TempoFlow<TempoAction> resolveConflicts = flow.of(RaiseConflictAction.class)
			.map(conflict -> {
				TempoAtom winner = conflict.allAtoms()
					.filter(a -> a.getTemporalProof().hasVertexByNID(self))
					.map(a -> Pair.of(a.getTemporalProof().getVertexByNID(self).getClock(), a))
					.min(Comparator.comparingLong(Pair::getFirst))
					.map(Pair::getSecond)
					.orElseThrow(() -> new TempoException("Error while resolving conflict, no atom has vertex by self"));
				Set<AID> allAids = conflict.allAids().collect(Collectors.toSet());
				return new OnConflictResolvedAction(winner, allAids, conflict.getTag());
			});
		return Stream.of(resolveConflicts);
	}
}
