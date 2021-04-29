/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.utils.functional;

import java.text.MessageFormat;
import java.util.Objects;

/**
 * Simplest failure descriptor.
 */
public interface Failure {
	String message();

	int code();

	default <T> Result<T> result() {
		return Result.fail(this);
	}

	default <T> Failure with(Object... params) {
		return failure(code(), message(), params);
	}

	/**
	 * Create instance of Failure with given code, message format string and parameters.
	 *
	 * @param code format string
	 * @param format message format string
	 * @param values parameters
	 *
	 * @return created instance of Failure
	 *
	 * @see MessageFormat for supported format string options
	 */
	static Failure failure(int code, String format, Object... values) {
		return failure(code, MessageFormat.format(format, values));
	}

	/**
	 * Create instance of Failure with given message and code.
	 *
	 * @param message failure message
	 *
	 * @return created instance of Failure
	 */
	static Failure failure(int code, String message) {
		return new Failure() {
			@Override
			public String message() {
				return message;
			}

			@Override
			public int code() {
				return code;
			}

			@Override
			public int hashCode() {
				return Objects.hash(message);
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == this) {
					return true;
				}

				return (obj instanceof Failure) && Objects.equals(((Failure) obj).message(), message);
			}

			@Override
			public String toString() {
				return message;
			}
		};
	}
}
