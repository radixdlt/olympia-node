package org.radix.atoms.events;

import org.radix.atoms.PreparedAtom;

public final class AtomStoredEvent extends PreparedAtomEvent {
	public AtomStoredEvent(PreparedAtom preparedAtom) {
		super(preparedAtom);
	}
}
