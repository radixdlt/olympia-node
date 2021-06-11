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
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.StateReducer;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces radix engine to stake received
 */
public final class MyValidatorStakesReducer implements StateReducer<MyValidator> {
	private final ECPublicKey key;

	@Inject
	public MyValidatorStakesReducer(@Self ECPublicKey key) {
		this.key = Objects.requireNonNull(key);
	}

	@Override
	public Class<MyValidator> stateClass() {
		return MyValidator.class;
	}

	@Override
	public Set<Class<? extends Particle>> particleClasses() {
		return Set.of(StakeOwnership.class, ValidatorStake.class);
	}

	@Override
	public Supplier<MyValidator> initial() {
		return MyValidator::new;
	}

	@Override
	public BiFunction<MyValidator, Particle, MyValidator> outputReducer() {
		return (stakes, p) -> {
			if (p instanceof StakeOwnership) {
				var d = (StakeOwnership) p;
				if (d.getDelegateKey().equals(key)) {
					stakes.addOwnership(d.getOwner(), d.getAmount());
				}
			} else if (p instanceof ValidatorStake) {
				var d = (ValidatorStake) p;
				if (d.getValidatorKey().equals(key)) {
					stakes.setStake(d);
				}
			}
			return stakes;
		};
	}

	@Override
	public BiFunction<MyValidator, Particle, MyValidator> inputReducer() {
		return (stakes, p) -> {
			if (p instanceof StakeOwnership) {
				var d = (StakeOwnership) p;
				if (d.getDelegateKey().equals(key)) {
					stakes.removeOwnership(d.getOwner(), d.getAmount());
				}
			}
			return stakes;
		};
	}
}
