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
	private final RRI rri;
	private final String description;
	private final String iconUrl;
	private final BigDecimal initialSupply;
	private final BigDecimal granularity;
	private final TokenSupplyType tokenSupplyType;

	private CreateTokenAction(
		RRI rri,
		String name,
		String description,
		String iconUrl,
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
		this.rri = Objects.requireNonNull(rri);
		this.description = description;
		this.iconUrl = iconUrl;
		this.initialSupply = initialSupply;
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenSupplyType = Objects.requireNonNull(tokenSupplyType);
	}

	public static CreateTokenAction create(
		RRI tokenRRI,
		String name,
		String description,
		String iconUrl,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		return new CreateTokenAction(tokenRRI, name, description, iconUrl, initialSupply, granularity, tokenSupplyType);
	}

	public static CreateTokenAction create(
		RRI tokenRRI,
		String name,
		String description,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		return create(tokenRRI, name, description, null, initialSupply, granularity, tokenSupplyType);
	}

	public RRI getRRI() {
		return rri;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getIconUrl() {
		return iconUrl;
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

	@Override
	public String toString() {
		return "CREATE TOKEN " + rri + " " + tokenSupplyType;
	}
}
