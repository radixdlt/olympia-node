package com.radixdlt.client.application.actions;

import com.radixdlt.client.core.atoms.AccountReference;

public class FixedSupplyTokenCreation {
	private final String name;
	private final String iso;
	private final String description;
	private final long fixedSupply;
	private final AccountReference accountReference;

	public FixedSupplyTokenCreation(
		AccountReference accountReference,
		String name,
		String iso,
		String description,
		long fixedSupply
	) {
		this.accountReference = accountReference;
		this.name = name;
		this.iso = iso;
		this.description = description;
		this.fixedSupply = fixedSupply;
	}

	public AccountReference getAccountReference() {
		return accountReference;
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
