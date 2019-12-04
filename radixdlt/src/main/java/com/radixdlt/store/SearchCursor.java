package com.radixdlt.store;

import com.radixdlt.common.AID;

/**
 * A ledger cursor, bound to a specific ledger instance.
 */
public interface SearchCursor {

	/**
	 * Gets the type of cursor
	 * @return The type of cursor
	 */
	StoreIndex.LedgerIndexType getType();

	/**
	 * Gets the current AID at this cursor
 	 * @return The current AID
	 */
	AID get();

	SearchCursor next();

	SearchCursor previous();

	SearchCursor first();

	SearchCursor last();
}
