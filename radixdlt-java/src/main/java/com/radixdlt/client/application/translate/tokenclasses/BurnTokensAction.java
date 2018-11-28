package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.util.Objects;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;

public class BurnTokensAction implements Action {
	private final TokenClassReference tokenClassReference;
	private final long amount;

	public BurnTokensAction(TokenClassReference tokenClassReference, long amount) {
		this.tokenClassReference = Objects.requireNonNull(tokenClassReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public TokenClassReference getTokenClassReference() {
		return tokenClassReference;
	}

	public BigDecimal getAmount() {
		return new BigDecimal(amount);
	}
}
