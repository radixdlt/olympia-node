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

import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_MAX;

public final class ValidatorRakeCopy implements ValidatorData {
	private final ECPublicKey validatorKey;
	private final int curRakePercentage;
	private final OptionalLong epochUpdate;

	public ValidatorRakeCopy(OptionalLong epochUpdate, ECPublicKey validatorKey, int curRakePercentage) {
		this.epochUpdate = epochUpdate;
		this.validatorKey = Objects.requireNonNull(validatorKey);
		this.curRakePercentage = curRakePercentage;
	}

	public ValidatorRakeCopy(ECPublicKey validatorKey, int curRakePercentage) {
		this.epochUpdate = OptionalLong.empty();
		this.validatorKey = Objects.requireNonNull(validatorKey);
		this.curRakePercentage = curRakePercentage;
	}

	public static ValidatorRakeCopy createVirtual(ECPublicKey validatorKey) {
		return new ValidatorRakeCopy(validatorKey, RAKE_MAX);
	}

	public OptionalLong getEpochUpdate() {
		return epochUpdate;
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	public int getRakePercentage() {
		return curRakePercentage;
	}

	@Override
	public int hashCode() {
		return Objects.hash(epochUpdate, validatorKey, curRakePercentage);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ValidatorRakeCopy)) {
			return false;
		}
		var other = (ValidatorRakeCopy) o;
		return Objects.equals(this.epochUpdate, other.epochUpdate)
			&& Objects.equals(this.validatorKey, other.validatorKey)
			&& this.curRakePercentage == other.curRakePercentage;
	}
}