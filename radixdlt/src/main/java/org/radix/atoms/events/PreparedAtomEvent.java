package org.radix.atoms.events;

import org.radix.atoms.PreparedAtom;

public abstract class PreparedAtomEvent extends AtomEvent {
	private final PreparedAtom preparedAtom;

	public PreparedAtomEvent(PreparedAtom preparedAtom) {
		super(preparedAtom.getAtom());
		this.preparedAtom = preparedAtom;
	}

	public PreparedAtom getPreparedAtom() {
		return preparedAtom;
	}
}
