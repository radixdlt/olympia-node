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
public final class Failure {
	private final String message;

	private Failure(String message) {
		this.message = message;
	}

	public String message() {
		return message;
	}

	@Override
	public int hashCode() {
		return Objects.hash(message);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}

		return (obj instanceof Failure) && Objects.equals(((Failure) obj).message(), message);
	}

	@Override
	public String toString() {
		return message;
	}

	public static Failure failure(final String message) {
		return new Failure(message);
	}

	public static Failure failure(final String format, Object... values) {
		return new Failure(MessageFormat.format(format, values));
	}
}
