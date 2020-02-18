package com.radixdlt.consensus;

/**
 * Represents a timeout in the pacemaker
 */
public final class Timeout {
	private final long round;

	public Timeout(long round) {
		this.round = round;
	}

	public long getRound() {
		return round;
	}
}
