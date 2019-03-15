package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
	private final TokenDefinitionReference tokenDefinitionReference;
	private final BigDecimal available;
	private final BigDecimal requestedAmount;

	public InsufficientFundsException(TokenDefinitionReference tokenDefinitionReference, BigDecimal available, BigDecimal requestedAmount) {
		super("Requested " + requestedAmount
			+ " but only " + available + " " + tokenDefinitionReference.getSymbol() + " available.");
		this.tokenDefinitionReference = tokenDefinitionReference;
		this.available = available;
		this.requestedAmount = requestedAmount;
	}

	public BigDecimal getAvailable() {
		return available;
	}

	public BigDecimal getRequestedAmount() {
		return requestedAmount;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof InsufficientFundsException)) {
			return false;
		}

		InsufficientFundsException o = (InsufficientFundsException) obj;
		return this.tokenDefinitionReference.equals(o.tokenDefinitionReference)
			&& this.available.compareTo(o.available) == 0
			&& this.requestedAmount.compareTo(o.requestedAmount) == 0;
	}

	@Override
	public int hashCode() {
		return this.getMessage().hashCode();
	}
}
