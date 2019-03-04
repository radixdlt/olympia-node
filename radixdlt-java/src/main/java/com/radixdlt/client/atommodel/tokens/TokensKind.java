package com.radixdlt.client.atommodel.tokens;

public enum TokensKind {
	MINTED("mint"),
	TRANSFERRED("transfer"),
	BURNED("burn");

	private final String verb;

	TokensKind(String verb) {
		this.verb = verb;
	}

	public String getVerbName() {
		return verb;
	}

	public static TokensKind fromVerbName(String verb) {
		for (TokensKind type : TokensKind.values()) {
			if (type.verb.equals(verb)) {
				return type;
			}
		}

		throw new IllegalArgumentException("Unknown fungible type verb: " + verb);
	}
}
