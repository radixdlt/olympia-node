package com.radixdlt.consensus;

/**
 * Represents a BFT round used by the Pacemaker of a BFT instance
 */
public final class Round implements Comparable<Round> {
	private final long round;

	private Round(long round) {
		if (round < 0) {
			throw new IllegalArgumentException("round must be >= 0 but was " + round);
		}

		this.round = round;
	}

	public static Round create(long round) {
		return new Round(round);
	}

	public Round next() {
		if (this.round == Long.MAX_VALUE) {
			throw new RuntimeException("Round Overflow");
		}

		return new Round(round + 1);
	}

	@Override
	public int compareTo(Round otherRound) {
		return Long.compare(this.round, otherRound.round);
	}

	@Override
	public int hashCode() {
		return Long.hashCode(round);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Round)) {
			return false;
		}

		Round round = (Round) o;
		return round.round == this.round;
	}


	@Override
	public String toString() {
		return "Round " + this.round;
	}
}
