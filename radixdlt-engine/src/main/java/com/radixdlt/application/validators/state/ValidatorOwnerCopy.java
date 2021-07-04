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
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class ValidatorOwnerCopy implements ValidatorData {
	private final ECPublicKey validatorKey;
	private final Optional<REAddr> owner;
	private final OptionalLong epochUpdate;

	public ValidatorOwnerCopy(
		OptionalLong epochUpdate,
		ECPublicKey validatorKey,
		Optional<REAddr> owner
	) {
		this.epochUpdate = epochUpdate;
		this.validatorKey = validatorKey;
		this.owner = owner;
	}

	public ValidatorOwnerCopy(
		ECPublicKey validatorKey,
		Optional<REAddr> owner
	) {
		this.epochUpdate = OptionalLong.empty();
		this.validatorKey = validatorKey;
		this.owner = owner;
	}

	public OptionalLong getEpochUpdate() {
		return epochUpdate;
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	public Optional<REAddr> getOwner() {
		return this.owner;
	}

	@Override
	public int hashCode() {
		return Objects.hash(epochUpdate, validatorKey, owner);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ValidatorOwnerCopy)) {
			return false;
		}

		var other = (ValidatorOwnerCopy) o;
		return
			Objects.equals(this.epochUpdate, other.epochUpdate)
			&& Objects.equals(this.validatorKey, other.validatorKey)
			&& Objects.equals(this.owner, other.owner);
	}
}
