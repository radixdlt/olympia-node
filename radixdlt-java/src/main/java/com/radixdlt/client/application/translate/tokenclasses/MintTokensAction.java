package com.radixdlt.client.application.translate.tokenclasses;

import java.util.Objects;

import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;

public class MintTokensAction implements Action {
	private final TokenClassReference tokenClassReference;
	private final UInt256 amount;

	public MintTokensAction(TokenClassReference tokenClassReference, UInt256 amount) {
		this.tokenClassReference = Objects.requireNonNull(tokenClassReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public TokenClassReference getTokenClassReference() {
		return tokenClassReference;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
