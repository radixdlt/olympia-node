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
	private final Map<ProcedureKey, Procedure> procedures;

	public Procedures(Map<ProcedureKey, Procedure> procedures) {
		this.procedures = procedures;
	}

	public static Procedures empty() {
		return new Procedures(Map.of());
	}

	public Procedures combine(Procedures other) {
		var combinedProcedures =
			Stream.concat(
				this.procedures.entrySet().stream(),
				other.procedures.entrySet().stream()
			).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		return new Procedures(combinedProcedures);
	}

	public Procedure getProcedure(ProcedureKey key) throws MissingProcedureException {
		var procedure = procedures.get(key);
		if (procedure == null) {
			throw new MissingProcedureException(key);
		}
		return procedure;
	}
}
