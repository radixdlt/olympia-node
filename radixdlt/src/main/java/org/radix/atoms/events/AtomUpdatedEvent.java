package org.radix.atoms.events;

import org.radix.atoms.PreparedAtom;

public class AtomUpdatedEvent extends PreparedAtomEvent {
	public AtomUpdatedEvent(PreparedAtom preparedAtom) {
		super(preparedAtom);
	}
}
