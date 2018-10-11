package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.TokenReference;
import com.radixdlt.client.application.objects.Amount;

public class InsufficientFundsException extends Exception {
	private final TokenReference tokenReference;
	private final long available;
	private final long requestedAmount;

	public InsufficientFundsException(TokenReference tokenReference, long available, long requestedAmount) {
		super("Requested " + Amount.subUnitsOf(requestedAmount, tokenReference)
			+ " but only " + Amount.subUnitsOf(available, tokenReference) + " available.");
		this.tokenReference = tokenReference;
		this.available = available;
		this.requestedAmount = requestedAmount;
	}

	public long getAvailable() {
		return available;
	}

	public long getRequestedAmount() {
		return requestedAmount;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof InsufficientFundsException)) {
			return false;
		}

		InsufficientFundsException o = (InsufficientFundsException) obj;
		return this.tokenReference.equals(o.tokenReference) && this.available == o.available && this.requestedAmount == o.requestedAmount;
	}

	@Override
	public int hashCode() {
		return this.getMessage().hashCode();
	}
}
