package com.radixdlt.ledger.exceptions;

import com.radixdlt.Atom;

public class AtomAlreadyExistsException extends LedgerException {
	private final Atom atom;

	public AtomAlreadyExistsException(Atom atom) {
		super("Atom '" + atom.getAID() + "' already exists");
		this.atom = atom;
	}
}
