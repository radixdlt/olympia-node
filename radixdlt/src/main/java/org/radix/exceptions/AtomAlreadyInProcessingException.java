package org.radix.exceptions;

import com.radixdlt.atoms.Atom;

public class AtomAlreadyInProcessingException extends IllegalStateException {
	private final Atom atom;

	public AtomAlreadyInProcessingException(Atom atom) {
		super("Atom is already being processed: " + atom.getAID());

		this.atom = atom;
	}

	public Atom getAtom() {
		return atom;
	}
}
