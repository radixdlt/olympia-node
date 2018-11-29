package com.radixdlt.client.application.translate;

/**
 * Exception describing an issue occurring when trying to execute a ledger action
 */
public class ActionExecutionException extends RuntimeException {
	public ActionExecutionException() {
		super();
	}

	public ActionExecutionException(String message) {
		super(message);
	}
}
