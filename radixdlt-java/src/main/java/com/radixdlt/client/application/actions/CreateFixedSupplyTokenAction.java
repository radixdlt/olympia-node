package com.radixdlt.client.application.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

public class CreateFixedSupplyTokenAction {
	private final String name;
	private final String iso;
	private final String description;
	private final long fixedSupply;
	private final RadixAddress address;

	public CreateFixedSupplyTokenAction(
		RadixAddress address,
		String name,
		String iso,
		String description,
		long fixedSupply
	) {
		Objects.requireNonNull(address);
		Objects.requireNonNull(iso);
		if (fixedSupply <= 0) {
			throw new IllegalArgumentException("Fixed supply must be greater than 0.");
		}

		this.address = address;
		this.name = name;
		this.iso = iso;
		this.description = description;
		this.fixedSupply = fixedSupply;
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

	public long getFixedSupply() {
		return fixedSupply;
	}
}
