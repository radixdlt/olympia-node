package com.radixdlt.consensus;

/**
 * Interface for an event coordinator to manage the pacemaker
 */
public interface Pacemaker {
	long getCurrentRound();
	void processTimeout();
	void processQC(long round);
}
