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

package com.radixdlt.application.validators.state;

import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;
import java.util.OptionalLong;

public final class ValidatorRegisteredCopy implements ValidatorData {
	private final ECPublicKey validatorKey;
	private final boolean isRegistered;
	private final OptionalLong epochUpdate;

	public ValidatorRegisteredCopy(
		ECPublicKey validatorKey,
		boolean isRegistered
	) {
		this.epochUpdate = OptionalLong.empty();
		this.validatorKey = validatorKey;
		this.isRegistered = isRegistered;
	}

	public ValidatorRegisteredCopy(
		OptionalLong epochUpdate,
		ECPublicKey validatorKey,
		boolean isRegistered
	) {
		this.epochUpdate = epochUpdate;
		this.validatorKey = validatorKey;
		this.isRegistered = isRegistered;
	}

	public OptionalLong getEpochUpdate() {
		return epochUpdate;
	}

	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	public boolean isRegistered() {
		return isRegistered;
	}

	@Override
	public int hashCode() {
		return Objects.hash(epochUpdate, validatorKey, isRegistered);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ValidatorRegisteredCopy)) {
			return false;
		}

		var other = (ValidatorRegisteredCopy) o;
		return Objects.equals(this.epochUpdate, other.epochUpdate)
			&& Objects.equals(this.validatorKey, other.validatorKey)
			&& this.isRegistered == other.isRegistered;
	}
}
