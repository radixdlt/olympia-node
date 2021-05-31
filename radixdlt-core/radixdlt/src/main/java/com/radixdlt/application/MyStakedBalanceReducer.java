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

import com.google.inject.Inject;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class MyStakedBalanceReducer implements StateReducer<MyStakedBalance> {
	private final REAddr accountAddr;

	@Inject
	public MyStakedBalanceReducer(@Self REAddr accountAddr) {
		this.accountAddr = Objects.requireNonNull(accountAddr);
	}

	@Override
	public Class<MyStakedBalance> stateClass() {
		return MyStakedBalance.class;
	}

	@Override
	public Set<Class<? extends Particle>> particleClasses() {
		return Set.of(StakeOwnership.class, ValidatorStake.class);
	}

	@Override
	public Supplier<MyStakedBalance> initial() {
		return MyStakedBalance::new;
	}

	@Override
	public BiFunction<MyStakedBalance, Particle, MyStakedBalance> outputReducer() {
		return (stakes, p) -> {
			if (p instanceof StakeOwnership) {
				var d = (StakeOwnership) p;
				if (d.getOwner().equals(accountAddr)) {
					stakes.addOwnership(d.getDelegateKey(), d.getAmount());
				}
			} else if (p instanceof ValidatorStake) {
				stakes.addValidatorStake((ValidatorStake) p);
			}
			return stakes;
		};
	}

	@Override
	public BiFunction<MyStakedBalance, Particle, MyStakedBalance> inputReducer() {
		return (balance, p) -> {
			if (p instanceof StakeOwnership) {
				var d = (StakeOwnership) p;
				if (d.getOwner().equals(accountAddr)) {
					balance.removeOwnership(d.getDelegateKey(), d.getAmount());
				}
			} else if (p instanceof ValidatorStake) {
				balance.removeValidatorStake((ValidatorStake) p);
			}
			return balance;
		};
	}
}
