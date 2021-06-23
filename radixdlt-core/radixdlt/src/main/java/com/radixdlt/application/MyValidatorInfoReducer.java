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
import com.radixdlt.atommodel.validators.state.AllowDelegationFlag;
import com.radixdlt.atommodel.validators.state.RakeCopy;
import com.radixdlt.atommodel.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.atommodel.validators.state.ValidatorState;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.StateReducer;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces radix engine state to validator info
 */
public final class MyValidatorInfoReducer implements StateReducer<MyValidatorInfo> {
	private final ECPublicKey self;

	@Inject
	public MyValidatorInfoReducer(@Self ECPublicKey self) {
		this.self = Objects.requireNonNull(self);
	}

	@Override
	public Class<MyValidatorInfo> stateClass() {
		return MyValidatorInfo.class;
	}

	@Override
	public Set<Class<? extends Particle>> particleClasses() {
		return Set.of(ValidatorParticle.class, RakeCopy.class, ValidatorOwnerCopy.class, AllowDelegationFlag.class);
	}

	@Override
	public Supplier<MyValidatorInfo> initial() {
		return () -> new MyValidatorInfo("", "", false, 0, true, null);
	}

	@Override
	public BiFunction<MyValidatorInfo, Particle, MyValidatorInfo> outputReducer() {
		return (i, p) -> {

			if (!(p instanceof ValidatorState)) {
				return i;
			}

			var validatorState = (ValidatorState) p;

			if (!validatorState.getValidatorKey().equals(self)) {
				return i;
			}

			if (p instanceof ValidatorParticle) {
				var r = (ValidatorParticle) p;
				return i.withNameUrlAndRegistration(r.getName(), r.getUrl(), r.isRegisteredForNextEpoch());
			}

			if (p instanceof RakeCopy) {
				var r = (RakeCopy) p;
				return i.withRake(r.getCurRakePercentage());
			}

			if (p instanceof ValidatorOwnerCopy) {
				var r = (ValidatorOwnerCopy) p;
				return i.withOwner(r.getOwner());
			}

			if (p instanceof AllowDelegationFlag) {
				var r = (AllowDelegationFlag) p;
				return i.withDelegation(r.allowsDelegation());
			}

			return i;
		};
	}

	@Override
	public BiFunction<MyValidatorInfo, Particle, MyValidatorInfo> inputReducer() {
		return (i, r) -> i;
	}
}
