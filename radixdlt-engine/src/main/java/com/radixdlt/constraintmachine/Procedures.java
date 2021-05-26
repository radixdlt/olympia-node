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
	private final Map<Pair<Class<? extends ReducerState>, Class<? extends Particle>>, UpProcedure<ReducerState, Particle>> upProcedures;
	private final Map<Pair<Class<? extends Particle>, Class<? extends ReducerState>>, DownProcedure<Particle, ReducerState>> downProcedures;
	private final Map<Class, EndProcedure<ReducerState>> endProcedures;

	public Procedures(
		Map<Pair<Class<? extends ReducerState>, Class<? extends Particle>>, UpProcedure<ReducerState, Particle>> upProcedures,
		Map<Pair<Class<? extends Particle>, Class<? extends ReducerState>>, DownProcedure<Particle, ReducerState>> downProcedures,
		Map<Class, EndProcedure<ReducerState>> endProcedures
	) {
		this.upProcedures = upProcedures;
		this.downProcedures = downProcedures;
		this.endProcedures = endProcedures;
	}

	public static Procedures empty() {
		return new Procedures(Map.of(), Map.of(), Map.of());
	}

	public Procedures combine(Procedures other) {
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

		var combinedEndProcedures =
			Stream.concat(
				this.endProcedures.entrySet().stream(),
				other.endProcedures.entrySet().stream()
			).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		return new Procedures(combinedUpProcedures, combinedDownProcedures, combinedEndProcedures);
	}

	public MethodProcedure getProcedure(
		REInstruction.REOp op,
		Object procedureParam,
		Class<? extends ReducerState> reducerStateClass
	) throws MissingProcedureException {
		MethodProcedure methodProcedure = null;
		if (op == REInstruction.REOp.END) {
			methodProcedure = endProcedures.get(reducerStateClass);
		} else if (op.getNextSpin() == Spin.UP) {
			var outputParticle = (Particle) procedureParam;
			methodProcedure = upProcedures.get(Pair.of(reducerStateClass, outputParticle.getClass()));
		} else if (op.getNextSpin() == Spin.DOWN) {
			var substateWithArg = (SubstateWithArg<Particle>) procedureParam;
			methodProcedure = downProcedures.get(Pair.of(substateWithArg.getSubstate().getClass(), reducerStateClass));
		}
		if (methodProcedure == null) {
			throw new MissingProcedureException();
		}
		return methodProcedure;
	}
}
