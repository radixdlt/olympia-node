package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;

import java.util.Set;

public class OnConflictResolvedAction implements TempoAction {
	private final Set<TempoAtom> allConflictingAtoms;
	private final TempoAtom winner;

	public OnConflictResolvedAction(TempoAtom winner, Set<TempoAtom> allConflictingAtoms) {
		this.allConflictingAtoms = allConflictingAtoms;
		this.winner = winner;
	}

	public Set<TempoAtom> getAllConflictingAtoms() {
		return allConflictingAtoms;
	}

	public TempoAtom getWinner() {
		return winner;
	}
}
