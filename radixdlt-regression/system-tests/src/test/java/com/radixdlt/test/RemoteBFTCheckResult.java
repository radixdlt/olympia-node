/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.test;

import io.reactivex.exceptions.CompositeException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A result of a single call to a {@link RemoteBFTCheck}
 */
public final class RemoteBFTCheckResult {
	private static final RemoteBFTCheckResult SUCCESS = new RemoteBFTCheckResult();
	private final Throwable exception;

	private RemoteBFTCheckResult() {
		this.exception = null;
	}

	private RemoteBFTCheckResult(Throwable exception) {
		this.exception = exception;
	}

	/**
	 * Asserts success, throwing an {@link AssertionError} with the given message in case this result is an error.
	 * @param errorMessage The message to include in case of error
	 */
	public void assertSuccess(String errorMessage) {
		if (!isSuccess()) {
			throw new AssertionError(errorMessage, this.exception);
		}
	}

	/**
	 * Tests whether this is a success result, defined as a result without an exception.
	 * @return Whether this is a success result
	 */
	public boolean isSuccess() {
		return this.exception == null;
	}

	/**
	 * Tests whether this is an error result, defined as a result with an exception.
	 * @return Whether this is an error result
	 */
	public boolean isError() {
		return !this.isSuccess();
	}

	/**
	 * Gets the exception underlying this result, may be null if this is not an error result.
	 * @return The exception underlying this result
	 */
	public Throwable getException() {
		return exception;
	}

	/**
	 * Translates an exception to a string, deconstructing inner {@link CompositeException}s recursively.
	 * @param exception The exception to translate
	 * @return A string representing the exception (plus any inner exception in case of composites)
	 */
	private static String exceptionToString(Throwable exception) {
		if (exception instanceof CompositeException) {
			return ((CompositeException) exception).getExceptions().stream()
				.map(RemoteBFTCheckResult::exceptionToString)
				.map(s -> String.format("{%s}", s))
				.collect(Collectors.joining(", "));
		} else {
			return exception.toString();
		}
	}

	@Override
	public String toString() {
		if (isSuccess()) {
			return "{success}";
		} else {
			return String.format("{error: %s}", exceptionToString(this.exception));
		}
	}

	/**
	 * Gets a success result
	 * @return A success result
	 */
	public static RemoteBFTCheckResult success() {
		return SUCCESS;
	}

	/**
	 * Creates an error result given a certain exception
	 * @param exception The exception
	 * @return The result representing the specified error result
	 */
	public static RemoteBFTCheckResult error(Throwable exception) {
		Objects.requireNonNull(exception, "exception must be specified for error results");
		return new RemoteBFTCheckResult(exception);
	}
}
