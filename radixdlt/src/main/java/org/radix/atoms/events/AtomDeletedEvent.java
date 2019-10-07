package org.radix.atoms.events;

import org.radix.atoms.PreparedAtom;

public final class AtomDeletedEvent extends AtomEventWithDestinations {

	public AtomDeletedEvent(PreparedAtom preparedAtom) {
		super(preparedAtom.getAtom(), null);
		throw new UnsupportedOperationException("PreparedAtom is deprecated");
	}
}
