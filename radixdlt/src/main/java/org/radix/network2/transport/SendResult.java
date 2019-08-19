package org.radix.network2.transport;

import java.io.IOException;
import java.util.Objects;

/**
 * Holds a success or failure result for a sent message.
 */
public class SendResult {
	private final static SendResult COMPLETE = new SendResult(null);

	/**
	 * Returns the successful {@code SendResult}.
	 *
	 * @return the successful {@code SendResult}
	 */
	public static final SendResult complete() {
		return COMPLETE;
	}

	/**
	 * Returns a freshly created failure result with the specified
	 * failure exception.
	 *
	 * @param exception The failure reason, must not be {@code null}
	 * @return a failure result with the specified exception reason
	 */
	public static final SendResult failure(IOException exception) {
		return new SendResult(Objects.requireNonNull(exception));
	}

	private final IOException exception;

	private SendResult(IOException exception) {
		this.exception = exception;
	}

	/**
	 * Returns {@code true} if this is a successful completion result.
	 *
	 * @return {@code true} if this is a successful completion result
	 */
	public boolean isComplete() {
		return this.exception == null;
	}

	/**
	 * Returns the exception reason if this is a failure result,
	 * or {@code null} if this is not a failure result.
	 *
	 * @return failure reason, or {@code null} if no failure
	 */
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
