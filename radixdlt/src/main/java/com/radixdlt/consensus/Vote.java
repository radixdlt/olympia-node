package com.radixdlt.consensus;

import java.util.Objects;

/**
 * Represents a vote on a vertex
 */
public final class Vote {
	private final int hash;

	public Vote(int hash) {
		this.hash = hash;
	}


	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vote)) {
			return false;
		}

		Vote v = (Vote) o;
		return Objects.equals(v.hash, this.hash);
	}
}
