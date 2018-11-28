package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;

public class MintTokensAction implements Action {
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
