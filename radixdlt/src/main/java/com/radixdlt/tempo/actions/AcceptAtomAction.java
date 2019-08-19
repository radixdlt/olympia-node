package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.reactive.TempoAction;

import java.util.Objects;

public class AcceptAtomAction implements TempoAction {
	private final TempoAtom atom;

	public AcceptAtomAction(TempoAtom atom) {
		this.atom = Objects.requireNonNull(atom, "atom is required");
	}

	public TempoAtom getAtom() {
		return atom;
	}
}
