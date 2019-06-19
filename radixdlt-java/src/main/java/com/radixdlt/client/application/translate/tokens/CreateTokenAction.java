package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import java.util.Objects;

import com.radixdlt.client.application.translate.Action;

public class CreateTokenAction implements Action {
	public enum TokenSupplyType {
		FIXED,
		MUTABLE
	}

	private final String name;
	private final RRI tokenRRI;
	private final String description;
	private final BigDecimal initialSupply;
	private final BigDecimal granularity;
	private final TokenSupplyType tokenSupplyType;

	private CreateTokenAction(
		RRI tokenRRI,
		String name,
		String description,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		// Redundant null check added for completeness
		Objects.requireNonNull(initialSupply);

		if (initialSupply.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Supply cannot be less than 0.");
		}

		if (tokenSupplyType.equals(TokenSupplyType.FIXED) && initialSupply.compareTo(BigDecimal.ZERO) == 0) {
			throw new IllegalArgumentException("Fixed supply must be greater than 0.");
		}

		this.name = Objects.requireNonNull(name);
		this.tokenRRI = Objects.requireNonNull(tokenRRI);
		this.description = description;
		this.initialSupply = initialSupply;
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenSupplyType = Objects.requireNonNull(tokenSupplyType);
	}

	public static CreateTokenAction create(
		RRI tokenRRI,
		String name,
		String description,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		return new CreateTokenAction(tokenRRI, name, description, initialSupply, granularity, tokenSupplyType);
	}

	public RRI getTokenRRI() {
		return tokenRRI;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getInitialSupply() {
		return initialSupply;
	}

	public BigDecimal getGranularity() {
		return granularity;
	}

	public TokenSupplyType getTokenSupplyType() {
		return tokenSupplyType;
	}
}
