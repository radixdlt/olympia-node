package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.application.translate.Action;
import java.math.BigDecimal;
import java.util.Objects;

public class BurnTokensAction implements Action {
	private final RadixAddress address;
	private final TokenDefinitionReference tokenDefinitionReference;
	private final BigDecimal amount;

	private BurnTokensAction(RadixAddress address, TokenDefinitionReference tokenDefinitionReference, BigDecimal amount) {
		this.address = Objects.requireNonNull(address);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public static BurnTokensAction create(
		RadixAddress address,
		TokenDefinitionReference tokenDefinitionReference,
		BigDecimal amount
	) {
		return new BurnTokensAction(address, tokenDefinitionReference, amount);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return tokenDefinitionReference;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
