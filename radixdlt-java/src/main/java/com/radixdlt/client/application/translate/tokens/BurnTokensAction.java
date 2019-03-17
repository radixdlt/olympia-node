package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;

public class BurnTokensAction implements Action {
	private final RadixAddress address;
	private final TokenDefinitionReference tokenDefinitionReference;
	private final UInt256 amount;

	public BurnTokensAction(RadixAddress address, TokenDefinitionReference tokenDefinitionReference, UInt256 amount) {
		this.address = Objects.requireNonNull(address);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return tokenDefinitionReference;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
