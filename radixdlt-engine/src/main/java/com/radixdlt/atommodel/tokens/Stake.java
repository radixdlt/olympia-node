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

package com.radixdlt.atommodel.tokens;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

public final class Stake implements Fungible {
	private final UInt256 amount;

	// Bucket keys
	private final ECPublicKey validatorKey;

	public Stake(
		UInt256 amount,
		ECPublicKey validatorKey
	) {
		this.validatorKey = Objects.requireNonNull(validatorKey);
		this.amount = Objects.requireNonNull(amount);
	}

	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]",
			getClass().getSimpleName(),
			amount,
			validatorKey
		);
	}

	@Override
	public UInt256 getAmount() {
		return this.amount;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Stake)) {
			return false;
		}
		var that = (Stake) o;
		return Objects.equals(validatorKey, that.validatorKey)
			&& Objects.equals(amount, that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			validatorKey,
			amount
		);
	}
}
