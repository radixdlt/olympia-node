package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;

public class BurnTokensAction implements Action {
	private final RadixAddress address;
	private final TokenTypeReference tokenTypeReference;
	private final UInt256 amount;

	public BurnTokensAction(RadixAddress address, TokenTypeReference tokenTypeReference, UInt256 amount) {
		this.address = Objects.requireNonNull(address);
		this.tokenTypeReference = Objects.requireNonNull(tokenTypeReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public TokenTypeReference getTokenTypeReference() {
		return tokenTypeReference;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
