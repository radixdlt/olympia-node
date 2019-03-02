package com.radixdlt.client.application.translate.tokenclasses;

import java.util.Objects;

import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;

public class MintTokensAction implements Action {
	private final TokenTypeReference tokenTypeReference;
	private final UInt256 amount;

	public MintTokensAction(TokenTypeReference tokenTypeReference, UInt256 amount) {
		this.tokenTypeReference = Objects.requireNonNull(tokenTypeReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public TokenTypeReference getTokenTypeReference() {
		return tokenTypeReference;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
