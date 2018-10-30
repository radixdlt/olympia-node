package com.radixdlt.client.core.address;

public enum RadixUniverseType {
	PUBLIC("RADIX_PUBLIC", 1),
	DEVELOPMENT("RADIX_DEVELOPMENT", 2);

	private final String name;
	private final int ordinalValue;

	RadixUniverseType(String name, int ordinalValue) {
		this.name = name;
		this.ordinalValue = ordinalValue;
	}

	public final int ordinalValue() {
		return this.ordinalValue;
	}

	public static RadixUniverseType valueOf(int ordinalValue) {
		for (RadixUniverseType universeType : RadixUniverseType.values()) {
			if (universeType.ordinalValue == ordinalValue) {
				return universeType;
			}
		}

		throw new IllegalArgumentException("No universe type of value: " + ordinalValue);
	}
}
