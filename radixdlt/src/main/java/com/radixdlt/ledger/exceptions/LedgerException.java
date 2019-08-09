package com.radixdlt.ledger.exceptions;

/**
 * Generic runtime exception in a {@link com.radixdlt.ledger.Ledger}
 */
public class LedgerException extends RuntimeException {
	public LedgerException(String message) {
		super(message);
	}

	public LedgerException(String message, Throwable cause) {
		super(message, cause);
	}

	public LedgerException(Throwable cause) {
		super(cause);
	}
}
