package com.radixdlt.client.application.translate;

public abstract class ActionExecutionExceptionReason {
	private final String message;

	public ActionExecutionExceptionReason(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
