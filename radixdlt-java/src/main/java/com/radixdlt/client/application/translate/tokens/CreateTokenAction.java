package com.radixdlt.client.application.translate.tokens;

import java.util.Objects;

import org.radix.utils.UInt256;

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
	private final UInt256 initialSupply;
	private final UInt256 granularity;
	private final RadixAddress address;
	private final TokenSupplyType tokenSupplyType;

	public CreateTokenAction(
		RadixAddress address,
		String name,
		String iso,
		String description,
		UInt256 initialSupply,
		UInt256 granularity,
		TokenSupplyType tokenSupplyType
	) {
		// Redundant null check added for completeness
		Objects.requireNonNull(initialSupply);
		if (tokenSupplyType.equals(TokenSupplyType.FIXED) && initialSupply.isZero()) {
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

	public UInt256 getInitialSupply() {
		return initialSupply;
	}

	public UInt256 getGranularity() {
		return granularity;
	}

	public TokenSupplyType getTokenSupplyType() {
		return tokenSupplyType;
	}
}
