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

import com.radixdlt.atom.Substate;

import java.util.Objects;

/**
 * Instruction which has been parsed and state checked by Radix Engine
 */
public final class ParsedInstruction {
	private final REInstruction instruction;
	private final Substate substate;
	private final Spin spin;

	private ParsedInstruction(REInstruction instruction, Substate substate, Spin spin) {
		Objects.requireNonNull(instruction);
		Objects.requireNonNull(substate);
		Objects.requireNonNull(spin);

		this.instruction = instruction;
		this.substate = substate;
		this.spin = spin;
	}

	public static ParsedInstruction of(REInstruction instruction, Substate substate, Spin spin) {
		return new ParsedInstruction(instruction, substate, spin);
	}

	public REInstruction getInstruction() {
		return instruction;
	}

	public Substate getSubstate() {
		return substate;
	}

	public Particle getParticle() {
		return substate.getParticle();
	}

	public <T extends Particle> T getParticle(Class<T> cls) {
		return cls.cast(substate.getParticle());
	}

	public Spin getSpin() {
		return spin;
	}

	public boolean isUp() {
		return this.spin.equals(Spin.UP);
	}

	public boolean isDown() {
		return this.spin.equals(Spin.DOWN);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ParsedInstruction)) {
			return false;
		}

		ParsedInstruction parsedInstruction = (ParsedInstruction) obj;

		return Objects.equals(this.substate, parsedInstruction.substate) && Objects.equals(this.spin, parsedInstruction.spin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(substate, spin);
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), spin, substate);
	}
}
