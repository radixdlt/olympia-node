package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.serialization.HasOrdinalValue;

public enum Spin implements HasOrdinalValue {
	UP(1), DOWN(2);

	private final int value;

	Spin(int value) {
		this.value = value;
	}

	public int ordinalValue() {
		return value;
	}

	public static Spin valueOf(int ordinalValue) {
		switch (ordinalValue) {
			case 1: return UP;
			case 2: return DOWN;
			default: throw new IllegalArgumentException("No universe type of value: " + ordinalValue);
		}
	}
}
