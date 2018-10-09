package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.objects.Token;
import com.radixdlt.client.application.objects.Amount;

public class InsufficientFundsException extends Exception {
	private final Token token;
	private final long available;
	private final long requestedAmount;

	public InsufficientFundsException(Token token, long available, long requestedAmount) {
		super("Requested " + Amount.subUnitsOf(requestedAmount, token)
			+ " but only " + Amount.subUnitsOf(available, token) + " available.");
		this.token = token;
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
		return this.token.equals(o.token) && this.available == o.available && this.requestedAmount == o.requestedAmount;
	}

	@Override
	public int hashCode() {
		return this.getMessage().hashCode();
	}
}
