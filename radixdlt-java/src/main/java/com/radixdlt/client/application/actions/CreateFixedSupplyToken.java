package com.radixdlt.client.application.actions;

import com.radixdlt.client.core.atoms.AccountReference;
import java.util.Objects;

public class CreateFixedSupplyToken {
	private final String name;
	private final String iso;
	private final String description;
	private final long fixedSupply;
	private final AccountReference accountReference;

	public CreateFixedSupplyToken(
		AccountReference accountReference,
		String name,
		String iso,
		String description,
		long fixedSupply
	) {
		Objects.requireNonNull(accountReference);
		Objects.requireNonNull(iso);
		if (fixedSupply <= 0) {
			throw new IllegalArgumentException("Fixed supply must be greater than 0.");
		}

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
