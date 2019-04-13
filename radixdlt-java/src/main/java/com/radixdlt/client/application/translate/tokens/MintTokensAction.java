package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import java.util.Objects;

import com.radixdlt.client.application.translate.Action;

public class MintTokensAction implements Action {
	private final RRI tokenDefinitionReference;
	private final BigDecimal amount;

	private MintTokensAction(RRI tokenDefinitionReference, BigDecimal amount) {
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public static MintTokensAction create(
		RRI tokenDefinitionReference,
		BigDecimal amount
	) {
		return new MintTokensAction(tokenDefinitionReference, amount);
	}

	public RRI getTokenDefinitionReference() {
		return tokenDefinitionReference;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
