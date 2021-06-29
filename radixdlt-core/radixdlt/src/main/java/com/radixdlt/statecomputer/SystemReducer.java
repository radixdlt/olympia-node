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

import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
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
		return Set.of(EpochData.class, RoundData.class);
	}

	@Override
	public Supplier<EpochView> initial() {
		return () -> new EpochView(0, View.of(0));
	}

	@Override
	public BiFunction<EpochView, Particle, EpochView> outputReducer() {
		return (cur, p) -> {
			if (p instanceof EpochData) {
				var s = (EpochData) p;
				return new EpochView(s.getEpoch(), cur.getView());
			} else if (p instanceof RoundData) {
				var s = (RoundData) p;
				return new EpochView(cur.getEpoch(), View.of(s.getView()));
			} else {
				throw new IllegalStateException();
			}
		};
	}

	@Override
	public BiFunction<EpochView, Particle, EpochView> inputReducer() {
		return (cur, p) -> cur;
	}
}
