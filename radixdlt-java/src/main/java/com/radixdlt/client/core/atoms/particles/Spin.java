package com.radixdlt.client.core.atoms.particles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Spin {
	UP(1), NEUTRAL(0), DOWN(-1);

	private final int value;

	Spin(int value) {
		this.value = value;
	}

	@JsonValue
	public int intValue() {
		return value;
	}

	@JsonCreator
	public static Spin valueOf(int intValue) {
		switch (intValue) {
			case 1: return UP;
			case 0: return NEUTRAL;
			case -1: return DOWN;
			default: throw new IllegalArgumentException("No spin type of value: " + intValue);
		}
	}
}
