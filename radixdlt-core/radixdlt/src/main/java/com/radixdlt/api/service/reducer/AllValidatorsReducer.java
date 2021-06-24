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

package com.radixdlt.api.service.reducer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.validators.state.AllowDelegationFlag;
import com.radixdlt.atommodel.validators.state.PreparedValidatorUpdate;
import com.radixdlt.atommodel.validators.state.RakeCopy;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.StateReducer;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces particles to all validators
 */
public final class AllValidatorsReducer implements StateReducer<AllValidators> {
	private final Logger logger = LogManager.getLogger();

	@Override
	public Class<AllValidators> stateClass() {
		return AllValidators.class;
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
			PreparedValidatorUpdate.class,
			AllowDelegationFlag.class,
			RakeCopy.class
		);
	}

	@Override
	public Supplier<AllValidators> initial() {
		return AllValidators::create;
	}

	@Override
	public BiFunction<AllValidators, Particle, AllValidators> outputReducer() {
		return (prev, p) -> {

			logger.info("Reducing {}", p.getClass().getSimpleName());

			if (p instanceof ValidatorParticle) {
				var v = (ValidatorParticle) p;
				return prev.add(v);
			} else if (p instanceof PreparedStake) { // TODO: Remove for mainnet
				var s = (PreparedStake) p;
				return prev.add(s.getDelegateKey(), s.getAmount());
			} else if (p instanceof PreparedValidatorUpdate) {
				var s = (PreparedValidatorUpdate) p;
				return prev.setOwner(s.getValidatorKey(), s.getOwnerAddress());
			} else if (p instanceof AllowDelegationFlag) {
				var s = (AllowDelegationFlag) p;
				return prev.setAllowDelegationFlag(s.getValidatorKey(), s.allowsDelegation());
			} else if (p instanceof RakeCopy) {
				var s = (RakeCopy) p;
				return prev.setRake(s.getValidatorKey(), s.getCurRakePercentage());
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
	public BiFunction<AllValidators, Particle, AllValidators> inputReducer() {
		return (prev, p) -> {
			if (p instanceof ValidatorParticle) {
				var v = (ValidatorParticle) p;
				return prev.remove(v);
			} else if (p instanceof PreparedStake) { // TODO: Remove for mainnet
				var s = (PreparedStake) p;
				return prev.remove(s.getDelegateKey(), s.getAmount());
			}

			return prev;
		};
	}
}
