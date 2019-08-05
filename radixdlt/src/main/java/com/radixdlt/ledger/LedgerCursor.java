package com.radixdlt.ledger;

import com.radixdlt.common.AID;

import java.io.IOException;

public interface LedgerCursor {
	enum Type {
		UNIQUE, DUPLICATE
	}

	Type getType();

	AID get();

	LedgerCursor next() throws IOException;

	LedgerCursor previous() throws IOException;

	LedgerCursor first() throws IOException;

	LedgerCursor last() throws IOException;
}
