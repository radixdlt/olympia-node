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

package com.radixdlt.statecomputer;

import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.StateReducer;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class CurrentValidatorsReducer implements StateReducer<CurrentValidators> {
	@Override
	public Class<CurrentValidators> stateClass() {
		return CurrentValidators.class;
	}

	@Override
	public Set<Class<? extends Particle>> particleClasses() {
		return Set.of(ValidatorBFTData.class);
	}

	@Override
	public Supplier<CurrentValidators> initial() {
		return CurrentValidators::create;
	}

	@Override
	public BiFunction<CurrentValidators, Particle, CurrentValidators> outputReducer() {
		return (s, p) -> {
			var validatorEpochData = (ValidatorBFTData) p;
			return s.add(validatorEpochData);
		};
	}

	@Override
	public BiFunction<CurrentValidators, Particle, CurrentValidators> inputReducer() {
		return (s, p) -> {
			var validatorEpochData = (ValidatorBFTData) p;
			return s.remove(validatorEpochData);
		};
	}
}
