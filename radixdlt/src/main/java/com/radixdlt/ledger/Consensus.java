package com.radixdlt.ledger;

import com.radixdlt.ledger.exceptions.ConsensusException;

/**
 * An instance of a consensus protocol which may be a participant in a network of nodes.
 * TODO this has been gutted and is now a temporary intermediate for consensus events
 */
public interface Consensus {
	/**
	 * Observes consensus, blocking until an observations becomes available.
	 *
	 * @return The consensus observation
	 *
	 * @throws ConsensusException in case of internal errors
	 */
	ConsensusObservation observe() throws InterruptedException;
}
