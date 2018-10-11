package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.TokenReference;
import java.math.BigDecimal;

public class InsufficientFundsException extends Exception {
	private final TokenReference tokenReference;
	private final BigDecimal available;
	private final BigDecimal requestedAmount;

	public InsufficientFundsException(TokenReference tokenReference, BigDecimal available, BigDecimal requestedAmount) {
		super("Requested " + requestedAmount
			+ " but only " + available + " " + tokenReference.getIso() + " available.");
		this.tokenReference = tokenReference;
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
		return this.tokenReference.equals(o.tokenReference)
			&& this.available.compareTo(o.available) == 0
			&& this.requestedAmount.compareTo(o.requestedAmount) == 0;
	}

	@Override
	public int hashCode() {
		return this.getMessage().hashCode();
	}
}
