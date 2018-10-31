package com.radixdlt.client.application.actions;

import com.radixdlt.client.atommodel.tokens.TokenClassReference;

public class MintTokensAction {
	private final TokenClassReference tokenClassReference;
	private final long amount;

	public MintTokensAction(TokenClassReference tokenClassReference, long amount) {
		this.tokenClassReference = tokenClassReference;
		this.amount = amount;
	}

	public TokenClassReference getTokenClassReference() {
		return tokenClassReference;
	}

	public long getAmount() {
		return amount;
	}
}
