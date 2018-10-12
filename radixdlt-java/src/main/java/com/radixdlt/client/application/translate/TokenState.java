package com.radixdlt.client.application.translate;

import java.math.BigDecimal;

public class TokenState {
	private final String name;
	private final String iso;
	private final String description;
	private final BigDecimal totalSupply;

	public TokenState(
		String name,
		String iso,
		String description,
		BigDecimal totalSupply
	) {
		this.name = name;
		this.iso = iso;
		this.description = description;
		this.totalSupply = totalSupply;
	}

	@Override
	public String toString() {
		return "Token(" + iso + ") name(" + name + ") description(" + description
			+ ") totalSupply(" + totalSupply + ") maxSupply(" + totalSupply + ")";
	}
}
