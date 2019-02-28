package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.tokens.TokenClassReference;

public class BurnTokensAction implements Action {
	private final RadixAddress address;
	private final TokenClassReference tokenClassReference;
	private final UInt256 amount;

	public BurnTokensAction(RadixAddress address, TokenClassReference tokenClassReference, UInt256 amount) {
		this.address = Objects.requireNonNull(address);
		this.tokenClassReference = Objects.requireNonNull(tokenClassReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public TokenClassReference getTokenClassReference() {
		return tokenClassReference;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
