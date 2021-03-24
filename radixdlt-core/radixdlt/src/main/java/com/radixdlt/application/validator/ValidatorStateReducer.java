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

package com.radixdlt.application.validator;

import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.RadixAddress;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces particles to the validator state of a single node
 */
public final class ValidatorStateReducer implements StateReducer<ValidatorState, RegisteredValidatorParticle> {

	private final RadixAddress radixAddress;

	public ValidatorStateReducer(RadixAddress radixAddress) {
		this.radixAddress = radixAddress;
	}

	@Override
	public Class<ValidatorState> stateClass() {
		return ValidatorState.class;
	}

	@Override
	public Class<RegisteredValidatorParticle> particleClass() {
		return RegisteredValidatorParticle.class;
	}

	@Override
	public Supplier<ValidatorState> initial() {
		return ValidatorState::unregistered;
	}

	@Override
	public BiFunction<ValidatorState, RegisteredValidatorParticle, ValidatorState> outputReducer() {
		return (prev, p) -> {
			if (!p.getAddress().equals(radixAddress)) {
				return prev;
			}
			return ValidatorState.registered(p);
		};
	}

	@Override
	public BiFunction<ValidatorState, RegisteredValidatorParticle, ValidatorState> inputReducer() {
		return (prev, p) -> {
			if (!p.getAddress().equals(radixAddress)) {
				return prev;
			}
			return ValidatorState.unregistered(p.getNonce() + 1);
		};
	}
}
