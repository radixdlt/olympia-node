package com.radixdlt.consensus;

/**
 * An instance of a consensus protocol which may be a participant in a network of nodes.
 * TODO this has been gutted and is now a temporary intermediate for consensus events
 */
public interface Consensus {
	/**
	 * Observes consensus, blocking until an observations becomes available.
	 *
	 * @return The consensus observation
	 */
	ConsensusObservation observe() throws InterruptedException;
}
