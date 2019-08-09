package org.radix.network2.transport;

import java.io.IOException;
import java.util.Objects;

public class SendResult {
	private final static SendResult COMPLETE = new SendResult(null);

	public static final SendResult complete() {
		return COMPLETE;
	}

	public static final SendResult failure(IOException exception) {
		return new SendResult(Objects.requireNonNull(exception));
	}

	private final IOException exception;

	private SendResult(IOException exception) {
		this.exception = exception;
	}

	public boolean isComplete() {
		return this.exception == null;
	}

	public IOException getException() {
		return this.exception;
	}

	@Override
	public String toString() {
		if (this.exception == null) {
			return String.format("%s[Complete]", getClass().getSimpleName());
		}
		return String.format("%s[%s]", getClass().getSimpleName(), exception.getClass().getName());
	}
}
