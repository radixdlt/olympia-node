package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.sync.SyncAction;

import java.util.Objects;

public class SyncAtomAction implements SyncAction {
	private final TempoAtom atom;

	public SyncAtomAction(TempoAtom atom) {
		this.atom = Objects.requireNonNull(atom, "atom is required");
	}

	public TempoAtom getAtom() {
		return atom;
	}
}
