package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import java.util.Objects;

public class BurnTokensAction implements Action {
	private final RadixAddress address;
	private final RRI tokenDefinitionReference;
	private final BigDecimal amount;

	private BurnTokensAction(RadixAddress address, RRI tokenDefinitionReference, BigDecimal amount) {
		this.address = Objects.requireNonNull(address);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public static BurnTokensAction create(
		RadixAddress address,
		RRI tokenDefinitionReference,
		BigDecimal amount
	) {
		return new BurnTokensAction(address, tokenDefinitionReference, amount);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public RRI getTokenDefinitionReference() {
		return tokenDefinitionReference;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
