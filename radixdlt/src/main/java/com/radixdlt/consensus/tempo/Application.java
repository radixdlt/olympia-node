package com.radixdlt.consensus.tempo;

import com.radixdlt.store.LedgerEntry;

/**
 * This is a temporary, rough interface representing the application-side part of the currently developing app/consensus interface.
 */
public interface Application {
	LedgerEntry takeNextEntry() throws InterruptedException;
}
