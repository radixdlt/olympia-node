package com.radixdlt.client.application.translate;

import java.util.Objects;

/**
 * A High level application error reason for action execution failure
 */
public abstract class ActionExecutionExceptionReason {
	private final String message;

	public ActionExecutionExceptionReason(String message) {
		Objects.requireNonNull(message);
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public String toString() {
		return message;
	}
}
