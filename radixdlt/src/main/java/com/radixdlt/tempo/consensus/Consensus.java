package com.radixdlt.tempo.consensus;

public interface Consensus {
	/**
	 * Observes consensus actions, waiting until one becomes available if required.
	 * @return The consensus action to take
	 */
	ConsensusAction observe() throws InterruptedException;
}
