/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.application.validator;

import java.util.Objects;

/**
 * Event signaling whether a node should be registered/unregistered
 * as a validator
 */
public final class ValidatorRegistration {
	private final boolean enabled;

	private ValidatorRegistration(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isRegister() {
		return enabled;
	}

	public static ValidatorRegistration register() {
		return new ValidatorRegistration(true);
	}

	public static ValidatorRegistration unregister() {
		return new ValidatorRegistration(false);
	}

	@Override
	public String toString() {
		return String.format("%s{%s}", this.getClass().getSimpleName(), this.enabled);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ValidatorRegistration)) {
			return false;
		}

		return this.enabled == ((ValidatorRegistration) o).enabled;
	}

	@Override
	public int hashCode() {
		return Objects.hash(enabled);
	}
}
