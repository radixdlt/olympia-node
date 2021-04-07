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
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ValidatorInfoReducer implements StateReducer<ValidatorInfo, RegisteredValidatorParticle> {
	private final RadixAddress address;

	@Inject
	public ValidatorInfoReducer(@Self RadixAddress address) {
		this.address = Objects.requireNonNull(address);
	}

	@Override
	public Class<ValidatorInfo> stateClass() {
		return ValidatorInfo.class;
	}

	@Override
	public Class<RegisteredValidatorParticle> particleClass() {
		return RegisteredValidatorParticle.class;
	}

	@Override
	public Supplier<ValidatorInfo> initial() {
		return () -> new ValidatorInfo(false);
	}

	@Override
	public BiFunction<ValidatorInfo, RegisteredValidatorParticle, ValidatorInfo> outputReducer() {
		return (i, r) -> {
			if (r.getAddress().equals(address)) {
				return new ValidatorInfo(true);
			}
			return i;
		};
	}

	@Override
	public BiFunction<ValidatorInfo, RegisteredValidatorParticle, ValidatorInfo> inputReducer() {
		return (i, r) -> {
			if (r.getAddress().equals(address)) {
				return new ValidatorInfo(false);
			}
			return i;
		};
	}
}
