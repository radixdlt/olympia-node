package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

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
	private final BigDecimal granularity;
	private final TokenSupplyType tokenSupplyType;

	public TokenState(
		String name,
		String iso,
		String description,
		BigDecimal totalSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		this.name = name;
		this.iso = iso;
		this.description = description;
		this.totalSupply = totalSupply;
		this.granularity = granularity;
		this.tokenSupplyType = tokenSupplyType;
	}

	public static TokenState combine(TokenState state0, TokenState state1) {
		return new TokenState(
			state0.name != null ? state0.name : state1.name,
			state0.iso != null ? state0.iso : state1.iso,
			state0.description != null ? state0.description : state1.description,
			Optional.ofNullable(state0.totalSupply).orElse(BigDecimal.ZERO)
				.add(Optional.ofNullable(state1.totalSupply).orElse(BigDecimal.ZERO)),
			state0.granularity != null ? state0.granularity : state1.granularity,
			state0.tokenSupplyType != null ? state0.tokenSupplyType : state1.tokenSupplyType
		);
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

	public BigDecimal getGranularity() {
		return this.granularity;
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
		return Objects.equals(this.name, tokenState.name)
			&& Objects.equals(this.iso, tokenState.iso)
			&& Objects.equals(this.tokenSupplyType, tokenState.tokenSupplyType)
			&& Objects.equals(this.description, tokenState.description)
			// Note BigDecimal.equal does not return true for different scales
			&& Objects.compare(this.granularity, tokenState.granularity, BigDecimal::compareTo) == 0
			&& Objects.compare(this.totalSupply, tokenState.totalSupply, BigDecimal::compareTo) == 0;
	}

	@Override
	public String toString() {
		return String.format("Token(%s) name(%s) description(%s) totalSupply(%s) granularity(%s) maxSupply(%s)",
			this.iso,
			this.name,
			this.description,
			this.totalSupply,
			this.granularity,
			this.totalSupply
		);
	}
}
