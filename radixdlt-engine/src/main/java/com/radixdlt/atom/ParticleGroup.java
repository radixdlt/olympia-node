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

package com.radixdlt.atom;

import com.google.common.collect.ImmutableList;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A group of particles representing one action, e.g. a transfer.
 */
public final class ParticleGroup {
	/**
	 * The particles and their spin contained within this {@link ParticleGroup}.
	 */
	private ImmutableList<CMInstruction> instructions;

	private ParticleGroup(Iterable<CMInstruction> instructions) {
		Objects.requireNonNull(instructions, "particles is required");

		this.instructions = ImmutableList.copyOf(instructions);
	}

	public List<CMInstruction> getInstructions() {
		return instructions;
	}

	public static ParticleGroupBuilder builder() {
		return new ParticleGroupBuilder();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ParticleGroup that = (ParticleGroup) o;
		return Objects.equals(instructions, that.instructions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(instructions);
	}

	/**
	 * A builder for immutable {@link ParticleGroup}s
	 */
	public static class ParticleGroupBuilder {
		private List<CMInstruction> instructions = new ArrayList<>();

		private ParticleGroupBuilder() {
		}

		public final ParticleGroupBuilder spinUp(Particle particle) {
			var particleDson = DefaultSerialization.getInstance().toDson(particle, DsonOutput.Output.ALL);
			this.instructions.add(
				CMInstruction.create(
					CMInstruction.CMOp.SPIN_UP.opCode(),
					particleDson
				)
			);
			return this;
		}

		public final ParticleGroupBuilder virtualSpinDown(Particle particle) {
			var particleDson = DefaultSerialization.getInstance().toDson(particle, DsonOutput.Output.ALL);
			this.instructions.add(
				CMInstruction.create(
					CMInstruction.CMOp.VIRTUAL_SPIN_DOWN.opCode(),
					particleDson
				)
			);
			return this;
		}

		public final ParticleGroupBuilder spinDown(SubstateId substateId) {
			this.instructions.add(CMInstruction.create(CMInstruction.CMOp.SPIN_DOWN.opCode(), substateId.asBytes()));
			return this;
		}

		public ParticleGroup build() {
			return new ParticleGroup(ImmutableList.copyOf(this.instructions));
		}
	}
}
