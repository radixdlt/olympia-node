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

import java.util.Objects;

import static com.radixdlt.utils.functional.Option.empty;
import static com.radixdlt.utils.functional.Option.option;

/**
 * Common interface for failures.
 */
public interface Failure {
	/**
	 * Retrieve message which describes the failure.
	 */
	String message();

	/**
	 * Retrieve possible cause of the failure.
	 */
	Option<?> cause();

	static Failure failure(String message) {
		return new FailureBase(message, empty());
	}

	static Failure failure(String message, Object cause) {
		return new FailureBase(message, option(cause));
	}

	final class FailureBase implements Failure {
		private final String message;
		private final Option<?> cause;

		private FailureBase(String message, Option<?> cause) {
			this.message = message;
			this.cause = cause;
		}

		@Override
		public String message() {
			return message;
		}

		@Override
		public Option<?> cause() {
			return cause;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}

			if (obj instanceof Failure) {
				var failure = (Failure) obj;
				return Objects.equals(message, failure.message()) && Objects.equals(cause, failure.cause());
			}

			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(message, cause);
		}

		@Override
		public String toString() {
			return "Failure('" + message + "', " + cause + ")";
		}
	}
}
