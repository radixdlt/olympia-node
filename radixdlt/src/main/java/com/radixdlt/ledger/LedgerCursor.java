package com.radixdlt.ledger;

import com.radixdlt.common.AID;

/**
 * A ledger cursor, bound to a specific ledger instance.
 */
public interface LedgerCursor {

	/**
	 * Gets the type of cursor
	 * @return The type of cursor
	 */
	LedgerIndex.LedgerIndexType getType();

	/**
	 * Gets the current AID at this cursor
 	 * @return The current AID
	 */
	AID get();

	LedgerCursor next();

	LedgerCursor previous();

	LedgerCursor first();

	LedgerCursor last();
}
