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

import java.util.stream.Collectors;

/**
 * A result of an ongoing {@link RemoteBFTCheck}
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

	public void assertSuccess(RemoteBFTCheck check) {
		if (!isSuccess()) {
			throw new AssertionError(String.format("check %s failed", check), this.exception);
		}
	}

	public boolean isSuccess() {
		return this.exception == null;
	}

	public boolean isError() {
		return !this.isSuccess();
	}

	public Throwable getException() {
		return exception;
	}

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

	public static RemoteBFTCheckResult success() {
		return SUCCESS;
	}

	public static RemoteBFTCheckResult error(Throwable exception) {
		return new RemoteBFTCheckResult(exception);
	}
}
