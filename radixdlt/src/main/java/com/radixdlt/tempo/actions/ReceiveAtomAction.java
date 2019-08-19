package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.reactive.TempoAction;

import java.util.Objects;

public class ReceiveAtomAction implements TempoAction {
	private final TempoAtom atom;

	public ReceiveAtomAction(TempoAtom atom) {
		this.atom = Objects.requireNonNull(atom, "atom is required");
	}

	public TempoAtom getAtom() {
		return atom;
	}
}
