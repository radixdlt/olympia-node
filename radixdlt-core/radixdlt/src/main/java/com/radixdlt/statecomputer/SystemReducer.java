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

import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.StateReducer;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces system level information
 */
class SystemReducer implements StateReducer<EpochView> {

	@Override
	public Class<EpochView> stateClass() {
		return EpochView.class;
	}

	@Override
	public Set<Class<? extends Particle>> particleClasses() {
		return Set.of(SystemParticle.class);
	}

	@Override
	public Supplier<EpochView> initial() {
		return () -> new EpochView(0, View.of(0));
	}

	@Override
	public BiFunction<EpochView, Particle, EpochView> outputReducer() {
		return (cur, p) -> {
			var s = (SystemParticle) p;
			return new EpochView(s.getEpoch(), View.of(s.getView()));
		};
	}

	@Override
	public BiFunction<EpochView, Particle, EpochView> inputReducer() {
		return (cur, p) -> cur;
	}
}
