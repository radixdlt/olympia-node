package com.radixdlt.consensus;

import java.util.Objects;

/**
 * Represents a new round in the pacemaker
 */
public final class NewRound {
	private final long round;

	public NewRound(long round) {
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
		NewRound newRound = (NewRound) o;
		return round == newRound.round;
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
