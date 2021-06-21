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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.validators.state.PreparedValidatorUpdate;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.StateReducer;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces particles to Registered Validators
 */
public final class StakedValidatorsReducer implements StateReducer<StakedValidators> {

	private final int minValidators;
	private final int maxValidators;

	@Inject
	public StakedValidatorsReducer(
		@MinValidators int minValidators,
		@MaxValidators int maxValidators
	) {
		this.minValidators = minValidators;
		this.maxValidators = maxValidators;
	}

	@Override
	public Class<StakedValidators> stateClass() {
		return StakedValidators.class;
	}

	@Override
	public Set<Class<? extends Particle>> particleClasses() {
		// Use immutable set here so that we can guarantee order
		// as there is a bit of a hack in that PreparedStake must be loaded
		// after ValidatorStake to get to the correct state
		return ImmutableSet.of(
			ValidatorParticle.class,
			ValidatorStakeData.class,
			PreparedStake.class,
			PreparedValidatorUpdate.class
		);
	}

	@Override
	public Supplier<StakedValidators> initial() {
		return () -> StakedValidators.create(minValidators, maxValidators);
	}

	@Override
	public BiFunction<StakedValidators, Particle, StakedValidators> outputReducer() {
		return (prev, p) -> {
			if (p instanceof ValidatorParticle) {
				var v = (ValidatorParticle) p;
				if (v.isRegisteredForNextEpoch()) {
					return prev.add(v);
				}
				return prev;
			} else if (p instanceof PreparedStake) { // TODO: Remove for mainnet
				var s = (PreparedStake) p;
				return prev.add(s.getDelegateKey(), s.getAmount());
			} else if (p instanceof PreparedValidatorUpdate) {
				var s = (PreparedValidatorUpdate) p;
				return prev.setOwner(s.getValidatorKey(), s.getOwnerAddress());
			} else {
				var s = (ValidatorStakeData) p;
				if (s.getOwnerAddr().isPresent()) {
					return prev.setOwner(s.getValidatorKey(), s.getOwnerAddr().get())
						.setStake(s.getValidatorKey(), s.getAmount());
				} else {
					return prev.setStake(s.getValidatorKey(), s.getAmount());
				}
			}
		};
	}

	@Override
	public BiFunction<StakedValidators, Particle, StakedValidators> inputReducer() {
		return (prev, p) -> {
			if (p instanceof ValidatorParticle) {
				var v = (ValidatorParticle) p;
				if (v.isRegisteredForNextEpoch()) {
					return prev.remove(v);
				}
			} else if (p instanceof PreparedStake) { // TODO: Remove for mainnet
				var s = (PreparedStake) p;
				return prev.remove(s.getDelegateKey(), s.getAmount());
			}

			return prev;
		};
	}
}
