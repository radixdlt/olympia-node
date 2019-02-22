package com.radixdlt.client.atommodel;

public enum FungibleType {
	MINTED("mint"),
	TRANSFERRED("transfer"),
	BURNED("burn");

	private final String verb;

	FungibleType(String verb) {
		this.verb = verb;
	}

	public String getVerbName() {
		return verb;
	}

	public static FungibleType fromVerbName(String verb) {
		for (FungibleType type : FungibleType.values()) {
			if (type.verb.equals(verb)) {
				return type;
			}
		}

		throw new IllegalArgumentException("Unknown fungible type verb: " + verb);
	}
}
