package com.radixdlt.client.application.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

public class CreateTokenAction {
	public enum TokenSupplyType {
		FIXED,
		MUTABLE
	}

	private final String name;
	private final String iso;
	private final String description;
	private final long initialSupply;
	private final RadixAddress address;
	private final TokenSupplyType tokenSupplyType;

	public CreateTokenAction(
		RadixAddress address,
		String name,
		String iso,
		String description,
		long initialSupply,
		TokenSupplyType tokenSupplyType
	) {
		if (initialSupply <= 0) {
			throw new IllegalArgumentException("Fixed supply must be greater than 0.");
		}

		this.address = Objects.requireNonNull(address);
		this.name = Objects.requireNonNull(name);
		this.iso = Objects.requireNonNull(iso);
		this.description = description;
		this.initialSupply = initialSupply;
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

	public long getInitialSupply() {
		return initialSupply;
	}

	public TokenSupplyType getTokenSupplyType() {
		return tokenSupplyType;
	}
}
