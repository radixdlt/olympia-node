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

import java.util.Objects;

public final class ProcedureKey {
	private final Class<? extends Particle> substateClass;
	private final Class<? extends ReducerState> reducerStateClass;

	private ProcedureKey(Class<? extends Particle> substateClass, Class<? extends ReducerState> reducerStateClass) {
		this.substateClass = substateClass;
		this.reducerStateClass = reducerStateClass;
	}

	public static ProcedureKey of(Class<? extends Particle> substateClass, Class<? extends ReducerState> reducerStateClass) {
		return new ProcedureKey(substateClass, reducerStateClass);
	}

	@Override
	public int hashCode() {
		return Objects.hash(substateClass, reducerStateClass);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ProcedureKey)) {
			return false;
		}

		var other = (ProcedureKey) o;
		return Objects.equals(this.substateClass, other.substateClass)
			&& Objects.equals(this.reducerStateClass, other.reducerStateClass);
	}

	@Override
	public String toString() {
		return String.format("%s{substate=%s reducer=%s}", this.getClass().getSimpleName(), substateClass, reducerStateClass);
	}
}
