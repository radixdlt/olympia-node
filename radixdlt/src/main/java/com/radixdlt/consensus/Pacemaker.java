package com.radixdlt.consensus;

/**
 * Interface for an event coordinator to manage the pacemaker
 */
public interface Pacemaker {
	long getCurrentRound();
	boolean processLocalTimeout(long round);
	void processQC(long round);
}
