package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;
import java.util.Objects;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

public class CreateTokenAction implements Action {
	public enum TokenSupplyType {
		FIXED,
		MUTABLE
	}

	private final String name;
	private final String iso;
	private final String description;
	private final BigDecimal initialSupply;
	private final BigDecimal granularity;
	private final RadixAddress address;
	private final TokenSupplyType tokenSupplyType;

	private CreateTokenAction(
		RadixAddress address,
		String name,
		String iso,
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

		this.address = Objects.requireNonNull(address);
		this.name = Objects.requireNonNull(name);
		this.iso = Objects.requireNonNull(iso);
		this.description = description;
		this.initialSupply = initialSupply;
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenSupplyType = Objects.requireNonNull(tokenSupplyType);
	}

	public static CreateTokenAction create(
		RadixAddress address,
		String name,
		String iso,
		String description,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		return new CreateTokenAction(address, name, iso, description, initialSupply, granularity, tokenSupplyType);
	}

	public RadixAddress getAddress() {
		return address;
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
