package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;
import java.util.Objects;

import com.radixdlt.client.application.translate.Action;

public class MintTokensAction implements Action {
	private final TokenDefinitionReference tokenDefinitionReference;
	private final BigDecimal amount;

	private MintTokensAction(TokenDefinitionReference tokenDefinitionReference, BigDecimal amount) {
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public static MintTokensAction create(
		TokenDefinitionReference tokenDefinitionReference,
		BigDecimal amount
	) {
		return new MintTokensAction(tokenDefinitionReference, amount);
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return tokenDefinitionReference;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
