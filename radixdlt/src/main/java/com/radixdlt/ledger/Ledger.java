package com.radixdlt.ledger;

import com.radixdlt.ledger.exceptions.LedgerException;

/**
 * An instance of a ledger which may be synchronised across a set of nodes.
 * TODO this has been gutted and is now a temporary intermediate for consensus events
 */
public interface Ledger {
	/**
	 * Observes this ledger, blocking until an observations becomes available.
	 *
	 * @return The ledger observation
	 *
	 * @throws LedgerException in case of internal errors
	 */
	LedgerObservation observe() throws InterruptedException;
}
