package com.radix.acceptance.atomic_transactions_with_dependence;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RadixAddress;

import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * An mint and transfer of a certain token as an atomic transaction.
 * Note: This is a semi-temporary action until this library supports atomic transactions, see RLAU-1050.
 */
public final class MintAndTransferTokensAction implements Action {
	private final RRI tokenDefinitionReference;
	private final BigDecimal amount;
	private final RadixAddress to;

	public MintAndTransferTokensAction(RRI tokenDefinitionReference, BigDecimal amount, RadixAddress to) {
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
		this.to = Objects.requireNonNull(to);
	}

	public RRI getTokenDefinitionReference() {
		return this.tokenDefinitionReference;
	}

	public RadixAddress getTo() {
		return to;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}
}
