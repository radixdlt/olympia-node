package com.radixdlt.consensus;

import java.util.Objects;

/**
 * Represents a new view in the pacemaker
 */
public final class NewView {
	private final long round;

	public NewView(long round) {
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
		NewView newView = (NewView) o;
		return round == newView.round;
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
