package com.radixdlt.tempo.actions;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;

import java.util.Set;

public class OnConflictResolvedAction implements TempoAction {
	private final Set<AID> allConflictingAids;
	private final TempoAtom winner;

	public OnConflictResolvedAction(TempoAtom winner, Set<AID> allConflictingAids) {
		this.allConflictingAids = allConflictingAids;
		this.winner = winner;
	}

	public Set<AID> getAllConflictingAids() {
		return allConflictingAids;
	}

	public TempoAtom getWinner() {
		return winner;
	}
}
