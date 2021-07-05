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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.constraintmachine.Particle;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * Reduces particles to Registered Validators
 */
public final class AllValidatorsReducer {
	public Set<Class<? extends Particle>> particleClasses() {
		// Use immutable set here so that we can guarantee order
		// as there is a bit of a hack in that PreparedStake must be loaded
		// after ValidatorStake to get to the correct state
		return ImmutableSet.of(
			ValidatorStakeData.class,
			ValidatorRegisteredCopy.class,
			PreparedStake.class,
			ValidatorOwnerCopy.class,
			AllowDelegationFlag.class,
			ValidatorMetaData.class,
			ValidatorRakeCopy.class
		);
	}

	public BiFunction<AllValidators, Particle, AllValidators> outputReducer() {
		return (prev, p) -> {
			if (p instanceof ValidatorRegisteredCopy) {
				var v = (ValidatorRegisteredCopy) p;
				return prev.add(v.getValidatorKey());
			} else if (p instanceof ValidatorMetaData) {
				var s = (ValidatorMetaData) p;
				return prev.set(s);
			} else if (p instanceof PreparedStake) { // TODO: Remove for mainnet
				var s = (PreparedStake) p;
				return prev.add(s.getDelegateKey(), s.getAmount());
			} else if (p instanceof ValidatorOwnerCopy) {
				var s = (ValidatorOwnerCopy) p;
				return prev.setOwner(s.getValidatorKey(), s.getOwner());
			} else if (p instanceof AllowDelegationFlag) {
				var s = (AllowDelegationFlag) p;
				return prev.setAllowDelegationFlag(s.getValidatorKey(), s.allowsDelegation());
			} else if (p instanceof ValidatorRakeCopy) {
				var s = (ValidatorRakeCopy) p;
				return prev.setRake(s.getValidatorKey(), s.getRakePercentage());
			} else {
				var s = (ValidatorStakeData) p;
				return prev.setOwner(s.getValidatorKey(), s.getOwnerAddr())
					.setStake(s.getValidatorKey(), s.getAmount())
					.setRegistered(s.getValidatorKey(), s.isRegistered());
			}
		};
	}
}
