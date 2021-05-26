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

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Procedures {
	private final Map<ProcedureKey, UpProcedure<ReducerState, Particle>> upProcedures;
	private final Map<ProcedureKey, DownProcedure<Particle, ReducerState>> downProcedures;
	private final Map<ProcedureKey, ShutdownAllProcedure<Particle, ReducerState>> shutdownAllProcedures;
	private final Map<ProcedureKey, EndProcedure<ReducerState>> endProcedures;

	public Procedures(
		Map<ProcedureKey, UpProcedure<ReducerState, Particle>> upProcedures,
		Map<ProcedureKey, DownProcedure<Particle, ReducerState>> downProcedures,
		Map<ProcedureKey, ShutdownAllProcedure<Particle, ReducerState>> shutdownAllProcedures,
		Map<ProcedureKey, EndProcedure<ReducerState>> endProcedures
	) {
		this.upProcedures = upProcedures;
		this.downProcedures = downProcedures;
		this.shutdownAllProcedures = shutdownAllProcedures;
		this.endProcedures = endProcedures;
	}

	public static Procedures empty() {
		return new Procedures(Map.of(), Map.of(), Map.of(), Map.of());
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

		var combinedShutdownAllProcedures =
			Stream.concat(
				this.shutdownAllProcedures.entrySet().stream(),
				other.shutdownAllProcedures.entrySet().stream()
			).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		var combinedEndProcedures =
			Stream.concat(
				this.endProcedures.entrySet().stream(),
				other.endProcedures.entrySet().stream()
			).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		return new Procedures(combinedUpProcedures, combinedDownProcedures, combinedShutdownAllProcedures, combinedEndProcedures);
	}

	public MethodProcedure getProcedure(
		REInstruction.REOp op,
		ProcedureKey key
	) throws MissingProcedureException {
		MethodProcedure methodProcedure = null;
		if (op == REInstruction.REOp.DOWNALL) {
			methodProcedure = shutdownAllProcedures.get(key);
		} else if (op == REInstruction.REOp.END) {
			methodProcedure = endProcedures.get(key);
		} else if (op.getNextSpin() == Spin.UP) {
			methodProcedure = upProcedures.get(key);
		} else if (op.getNextSpin() == Spin.DOWN) {
			methodProcedure = downProcedures.get(key);
		}
		if (methodProcedure == null) {
			throw new MissingProcedureException(op, key);
		}
		return methodProcedure;
	}
}
