package com.radixdlt.ledger.exceptions;

import com.radixdlt.common.AID;

public class AtomAlreadyExistsException extends LedgerException {
	private final AID atomId;

	public AtomAlreadyExistsException(AID atomId) {
		super("Atom with ID: '" + atomId + "' already exists");
		this.atomId = atomId;
	}

	public AID getAID() {
		return atomId;
	}
}
