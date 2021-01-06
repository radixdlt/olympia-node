/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.transport;

import java.util.Objects;

/**
 * Holds a success or failure result for a sent message.
 */
public class SendResult {
	private static final SendResult COMPLETE = new SendResult(null);

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
	 * @param throwable The failure reason, must not be {@code null}
	 * @return a failure result with the specified exception reason
	 */
	public static final SendResult failure(Throwable throwable) {
		return new SendResult(Objects.requireNonNull(throwable));
	}

	private final Throwable throwable;

	private SendResult(Throwable throwable) {
		this.throwable = throwable;
	}

	/**
	 * Returns {@code true} if this is a successful completion result.
	 *
	 * @return {@code true} if this is a successful completion result
	 */
	public boolean isComplete() {
		return this.throwable == null;
	}

	/**
	 * Returns the exception reason if this is a failure result,
	 * or {@code null} if this is not a failure result.
	 *
	 * @return failure reason, or {@code null} if no failure
	 */
	public Throwable getThrowable() {
		return this.throwable;
	}

	@Override
	public String toString() {
		if (this.throwable == null) {
			return String.format("%s[Complete]", getClass().getSimpleName());
		}
		return String.format("%s[%s]", getClass().getSimpleName(), throwable.getClass().getName());
	}
}
