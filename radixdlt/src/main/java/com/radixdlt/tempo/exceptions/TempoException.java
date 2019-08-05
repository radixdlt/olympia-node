package com.radixdlt.tempo.exceptions;

/**
 * A Tempo exception
 */
public class TempoException extends RuntimeException {
	public TempoException(String message) {
		super(message);
	}

	public TempoException(String message, Throwable cause) {
		super(message, cause);
	}

	public TempoException(Throwable cause) {
		super(cause);
	}

	public TempoException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
