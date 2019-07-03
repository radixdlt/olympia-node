package org.radix.atoms.events;

import org.radix.atoms.PreparedAtom;

public final class AtomDeletedEvent extends PreparedAtomEvent {
	public AtomDeletedEvent(PreparedAtom preparedAtom) {
		super(preparedAtom);
	}
}
