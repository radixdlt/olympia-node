package com.radixdlt.client.wallet;

public class InsufficientFundsException extends Exception {
	private final long available;
	private final long requestedAmount;

	public InsufficientFundsException(long available, long requestedAmount) {
		super("Requested " + requestedAmount + " XRD but only " + available + " XRD available.");
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

		InsufficientFundsException o = (InsufficientFundsException)obj;
		return this.available == o.available && this.requestedAmount == o.requestedAmount;
	}
}
