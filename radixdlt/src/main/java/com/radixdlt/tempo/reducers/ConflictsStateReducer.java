package com.radixdlt.tempo.reducers;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.reactive.TempoReducer;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.OnConflictResolvedAction;
import com.radixdlt.tempo.actions.ResolveConflictAction;
import com.radixdlt.tempo.state.ConflictsState;

public final class ConflictsStateReducer implements TempoReducer<ConflictsState> {
	@Override
	public Class<ConflictsState> stateClass() {
		return ConflictsState.class;
	}

	@Override
	public ConflictsState initialState() {
		return ConflictsState.empty();
	}

	@Override
	public ConflictsState reduce(ConflictsState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof ResolveConflictAction) {
			ResolveConflictAction conflict = (ResolveConflictAction) action;
			ImmutableSet<TempoAtom> allConflictingAtoms = conflict.allAtoms().collect(ImmutableSet.toImmutableSet());
			return prevState.with(conflict.getTag(), conflict.getAtom(), allConflictingAtoms);
		} else if (action instanceof OnConflictResolvedAction) {
			return prevState.without(((OnConflictResolvedAction) action).getTag());
		}

		return prevState;
	}
}
