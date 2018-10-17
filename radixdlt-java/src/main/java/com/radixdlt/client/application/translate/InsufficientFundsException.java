package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.TokenRef;
import java.math.BigDecimal;

public class InsufficientFundsException extends Exception {
	private final TokenRef tokenRef;
	private final BigDecimal available;
	private final BigDecimal requestedAmount;

	public InsufficientFundsException(TokenRef tokenRef, BigDecimal available, BigDecimal requestedAmount) {
		super("Requested " + requestedAmount
			+ " but only " + available + " " + tokenRef.getIso() + " available.");
		this.tokenRef = tokenRef;
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
		return this.tokenRef.equals(o.tokenRef)
			&& this.available.compareTo(o.available) == 0
			&& this.requestedAmount.compareTo(o.requestedAmount) == 0;
	}

	@Override
	public int hashCode() {
		return this.getMessage().hashCode();
	}
}
