package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The state and data of a token at a given moment in time
 */
public class TokenState {
	public enum TokenSupplyType {
		FIXED,
		MUTABLE
	}

	private final String name;
	private final String iso;
	private final String description;
	private final BigDecimal totalSupply;
	private final TokenSupplyType tokenSupplyType;

	public TokenState(
		String name,
		String iso,
		String description,
		BigDecimal totalSupply,
		TokenSupplyType tokenSupplyType
	) {
		this.name = name;
		this.iso = iso;
		this.description = description;
		this.totalSupply = totalSupply;
		this.tokenSupplyType = tokenSupplyType;
	}

	public String getName() {
		return name;
	}

	public String getIso() {
		return iso;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getTotalSupply() {
		return totalSupply;
	}

	public TokenSupplyType getTokenSupplyType() {
		return tokenSupplyType;
	}

	public BigDecimal getMaxSupply() {
		return totalSupply;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, iso, description, tokenSupplyType, totalSupply);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenState)) {
			return false;
		}

		TokenState tokenState = (TokenState) o;
		return this.name.equals(tokenState.name)
			&& this.iso.equals(tokenState.iso)
			&& this.tokenSupplyType.equals(tokenState.tokenSupplyType)
			&& this.description.equals(tokenState.description)
			&& this.totalSupply.equals(tokenState.totalSupply);
	}

	@Override
	public String toString() {
		return "Token(" + iso + ") name(" + name + ") description(" + description
			+ ") totalSupply(" + totalSupply + ") maxSupply(" + totalSupply + ")";
	}
}
