package com.radixdlt.tempo.exceptions;

import com.radixdlt.ledger.exceptions.LedgerException;

/**
 * A Tempo exception
 */
public class TempoException extends LedgerException {
	public TempoException(String message) {
		super(message);
	}

	public TempoException(String message, Throwable cause) {
		super(message, cause);
	}

	public TempoException(Throwable cause) {
		super(cause);
	}
}
