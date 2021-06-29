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

package com.radixdlt.application;

import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class MyStakedBalance {
	private final Map<ECPublicKey, ValidatorStakeData> validatorStakes = new ConcurrentHashMap<>();
	private final Map<ECPublicKey, UInt256> ownership = new ConcurrentHashMap<>();

	public void addValidatorStake(ValidatorStakeData validatorStake) {
		if (ownership.containsKey(validatorStake.getValidatorKey())) {
			validatorStakes.put(validatorStake.getValidatorKey(), validatorStake);
		}
	}

	public void removeValidatorStake(ValidatorStakeData validatorStake) {
		validatorStakes.remove(validatorStake.getValidatorKey());
	}

	public void addOwnership(ECPublicKey delegate, UInt256 amount) {
		ownership.merge(delegate, amount, UInt256::add);
	}

	public void removeOwnership(ECPublicKey delegate, UInt256 amount) {
		ownership.computeIfPresent(delegate, (d, cur) -> {
			var newAmt = cur.subtract(amount);
			return newAmt.isZero() ? null : newAmt;
		});
	}

	public void forEach(BiConsumer<ECPublicKey, UInt256> consumer) {
		this.ownership.forEach((delegate, ownership) -> {
			var validator = validatorStakes.get(delegate);
			if (validator == null) {
				return;
			}
			var totalStake = validator.getAmount();
			var totalOwnership = validator.getTotalOwnership();
			var stake = ownership.multiply(totalStake).divide(totalOwnership);
			consumer.accept(delegate, stake);
		});
	}
}
