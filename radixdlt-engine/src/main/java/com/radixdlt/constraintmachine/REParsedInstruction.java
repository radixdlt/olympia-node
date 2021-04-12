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
public final class REParsedInstruction {
	private final REInstruction instruction;
	private final Substate substate;

	private REParsedInstruction(REInstruction instruction, Substate substate) {
		Objects.requireNonNull(instruction);
		Objects.requireNonNull(substate);

		this.instruction = instruction;
		this.substate = substate;
	}

	public static REParsedInstruction of(REInstruction instruction, Substate substate) {
		return new REParsedInstruction(instruction, substate);
	}

	public REInstruction getInstruction() {
		return instruction;
	}

	public Spin getCheckSpin() {
		return instruction.getCheckSpin();
	}

	public Spin getNextSpin() {
		return instruction.getNextSpin();
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

	public boolean isStateUpdate() {
		return this.instruction.isPush();
	}

	public boolean isBootUp() {
		return this.instruction.isPush() && this.instruction.getNextSpin() == Spin.UP;
	}

	public boolean isShutDown() {
		return this.instruction.isPush() && this.instruction.getNextSpin() == Spin.DOWN;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof REParsedInstruction)) {
			return false;
		}

		REParsedInstruction parsedInstruction = (REParsedInstruction) obj;

		return Objects.equals(this.instruction, parsedInstruction.instruction)
			&& Objects.equals(this.substate, parsedInstruction.substate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(instruction, substate);
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), substate);
	}
}
