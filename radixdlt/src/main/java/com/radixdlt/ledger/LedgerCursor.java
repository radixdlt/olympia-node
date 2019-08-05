package com.radixdlt.ledger;

import com.radixdlt.common.AID;

import java.io.IOException;

public interface LedgerCursor {
	enum Type {
		UNIQUE, DUPLICATE
	}

	Type getType();

	AID get();

	LedgerCursor getNext() throws IOException;

	LedgerCursor getPrev() throws IOException;

	LedgerCursor getFirst() throws IOException;

	LedgerCursor getLast() throws IOException;
}
