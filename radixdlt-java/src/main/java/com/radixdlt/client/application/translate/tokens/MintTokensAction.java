package com.radixdlt.client.application.translate.tokens;

import java.util.Objects;

import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;

public class MintTokensAction implements Action {
	private final TokenDefinitionReference tokenDefinitionReference;
	private final UInt256 amount;

	public MintTokensAction(TokenDefinitionReference tokenDefinitionReference, UInt256 amount) {
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return tokenDefinitionReference;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
