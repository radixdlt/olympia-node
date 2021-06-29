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
import com.radixdlt.application.validators.state.PreparedRegisteredUpdate;
import com.radixdlt.application.validators.state.ValidatorMetaData;
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
		return Set.of(
			ValidatorMetaData.class,
			PreparedRegisteredUpdate.class
		);
	}

	@Override
	public Supplier<MyValidatorInfo> initial() {
		return () -> new MyValidatorInfo("", "", false);
	}

	@Override
	public BiFunction<MyValidatorInfo, Particle, MyValidatorInfo> outputReducer() {
		return (i, p) -> {
			if (p instanceof ValidatorMetaData) {
				var r = (ValidatorMetaData) p;
				if (r.getValidatorKey().equals(self)) {
					return new MyValidatorInfo(
						r.getName(),
						r.getUrl(),
						i.isRegistered()
					);
				}
			} else {
				var r = (PreparedRegisteredUpdate) p;
				if (r.getValidatorKey().equals(self)) {
					return new MyValidatorInfo(
						i.getName(),
						i.getUrl(),
						r.isRegistered()
					);
				}
			}
			return i;
		};
	}

	@Override
	public BiFunction<MyValidatorInfo, Particle, MyValidatorInfo> inputReducer() {
		return (i, r) -> i;
	}
}
