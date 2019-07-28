package com.radixdlt.ledger;

import java.io.IOException;

import com.radixdlt.common.AID;

public interface LedgerCursor
{
	public AID get();
	public LedgerCursor getNext() throws IOException;
	public LedgerCursor getPrev() throws IOException;
	public LedgerCursor getFirst() throws IOException;
	public LedgerCursor getLast() throws IOException;
}
