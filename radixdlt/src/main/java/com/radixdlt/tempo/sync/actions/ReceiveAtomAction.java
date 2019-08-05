package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.SyncAction;
import org.radix.atoms.Atom;

public class ReceiveAtomAction implements SyncAction {
	private final Atom atom;

	public ReceiveAtomAction(Atom atom) {
		this.atom = atom;
	}

	public Atom getAtom() {
		return atom;
	}
}
