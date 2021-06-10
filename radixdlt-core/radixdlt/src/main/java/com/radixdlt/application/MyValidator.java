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

import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Amount of stake received from each of one's delegators.
 */
public final class MyValidator {
	private UInt256 totalStake = UInt256.ZERO;
	private UInt256 totalOwnership = UInt256.ZERO;
	private final Map<REAddr, UInt256> ownership = new ConcurrentHashMap<>();

	public void setStake(ValidatorStake validatorStake) {
		this.totalOwnership = validatorStake.getTotalOwnership();
		this.totalStake = validatorStake.getAmount();
	}

	public void addOwnership(REAddr delegate, UInt256 amount) {
		ownership.merge(delegate, amount, UInt256::add);
	}

	public void removeOwnership(REAddr delegate, UInt256 amount) {
		ownership.computeIfPresent(delegate, (d, cur) -> {
			var newAmt = cur.subtract(amount);
			return newAmt.isZero() ? null : newAmt;
		});
	}

	public void forEach(BiConsumer<REAddr, UInt256> consumer) {
		this.ownership.forEach((addr, ownership) -> {
			var stake = ownership.multiply(totalStake).divide(totalOwnership);
			consumer.accept(addr, stake);
		});
	}

	public UInt256 getTotalStake() {
		return totalStake;
	}
}
