package com.radixdlt.constraintmachine;

/**
 * The state of a {@link Particle}
 */
public enum Spin {
	NEUTRAL(0),
	UP(1),
	DOWN(-1);

	private final int intValue;

	Spin(int intValue) {
		this.intValue = intValue;
	}

	public static Spin valueOf(int intValue) {
		switch (intValue) {
			case 1: return UP;
			case 0: return NEUTRAL;
			case -1: return DOWN;
			default: throw new IllegalArgumentException("No spin type of value: " + intValue);
		}
	}

	public int intValue() {
		return intValue;
	}

	@Override
	public String toString() {
		return this.name();
	}
}