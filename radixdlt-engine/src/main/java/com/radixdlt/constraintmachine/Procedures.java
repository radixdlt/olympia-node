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

package com.radixdlt.constraintmachine;

import com.radixdlt.utils.Pair;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Procedures {
	private final Map<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> oldProcedures;
	private final Map<Pair<Class<? extends ReducerState>, Class<? extends Particle>>, UpProcedure<ReducerState, Particle>> upProcedures;
	private final Map<Pair<Class<? extends Particle>, Class<? extends ReducerState>>, DownProcedure<Particle, ReducerState>> downProcedures;

	public Procedures(
		Map<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> oldProcedures,
		Map<Pair<Class<? extends ReducerState>, Class<? extends Particle>>, UpProcedure<ReducerState, Particle>> upProcedures,
		Map<Pair<Class<? extends Particle>, Class<? extends ReducerState>>, DownProcedure<Particle, ReducerState>> downProcedures
	) {
		this.oldProcedures = oldProcedures;
		this.upProcedures = upProcedures;
		this.downProcedures = downProcedures;
	}

	public static Procedures empty() {
		return new Procedures(Map.of(), Map.of(), Map.of());
	}

	public Procedures combine(Procedures other) {
		var combinedOldProcedures =
			Stream.concat(
				this.oldProcedures.entrySet().stream(),
				other.oldProcedures.entrySet().stream()
			).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		var combinedUpProcedures =
			Stream.concat(
				this.upProcedures.entrySet().stream(),
				other.upProcedures.entrySet().stream()
			).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		var combinedDownProcedures =
			Stream.concat(
				this.downProcedures.entrySet().stream(),
				other.downProcedures.entrySet().stream()
			).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		return new Procedures(combinedOldProcedures, combinedUpProcedures, combinedDownProcedures);
	}

	public TransitionProcedure<Particle, Particle, ReducerState> getFromOldProcedures(TransitionToken<?, ?, ?> transitionToken) {
		return oldProcedures.get(transitionToken);
	}

	public UpProcedure<ReducerState, Particle> getUpProcedure(
		Class<? extends ReducerState> reducerStateClass, Class<? extends Particle> upClass
	) {
		return upProcedures.get(Pair.of(reducerStateClass, upClass));
	}

	public DownProcedure<Particle, ReducerState> getDownProcedure(
		Class<? extends Particle> downClass, Class<? extends ReducerState> reducerStateClass
	) {
		return downProcedures.get(Pair.of(downClass, reducerStateClass));
	}
}
