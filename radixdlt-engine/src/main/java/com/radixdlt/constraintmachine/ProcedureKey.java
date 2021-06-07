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
	private final Class<? extends ReducerState> currentState;
	private final OpSignature opSignature;

	private ProcedureKey(Class<? extends ReducerState> currentState, OpSignature opSignature) {
		this.currentState = currentState;
		this.opSignature = opSignature;
	}

	public static ProcedureKey of(Class<? extends ReducerState> currentState, OpSignature opSignature) {
		return new ProcedureKey(currentState, opSignature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(opSignature, currentState);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ProcedureKey)) {
			return false;
		}

		var other = (ProcedureKey) o;
		return Objects.equals(this.opSignature, other.opSignature)
			&& Objects.equals(this.currentState, other.currentState);
	}

	@Override
	public String toString() {
		return String.format("%s{current=%s opSignature=%s}", this.getClass().getSimpleName(), currentState, opSignature);
	}
}
