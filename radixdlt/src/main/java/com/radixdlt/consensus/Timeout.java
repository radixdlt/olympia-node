package com.radixdlt.consensus;

import java.util.Objects;

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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Timeout timeout = (Timeout) o;
		return round == timeout.round;
	}

	@Override
	public int hashCode() {
		return Objects.hash(round);
	}

	@Override
	public String toString() {
		return "Timeout{" +
			"round=" + round +
			'}';
	}
}
