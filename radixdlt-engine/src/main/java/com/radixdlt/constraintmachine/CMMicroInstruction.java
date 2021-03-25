/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.constraintmachine;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.ParticleId;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DsonOutput;

import java.util.Objects;

public final class CMMicroInstruction {
	public enum CMMicroOp {
		SPIN_UP((byte) 1, Spin.NEUTRAL, Spin.UP),
		VIRTUAL_SPIN_DOWN((byte) 2, Spin.UP, Spin.DOWN),
		SPIN_DOWN((byte) 3, Spin.UP, Spin.DOWN),
		PARTICLE_GROUP((byte) 0, null, null);

		private final Spin checkSpin;
		private final Spin nextSpin;
		private final byte opCode;

		CMMicroOp(byte opCode, Spin checkSpin, Spin nextSpin) {
			this.opCode = opCode;
			this.checkSpin = checkSpin;
			this.nextSpin = nextSpin;
		}

		public byte opCode() {
			return opCode;
		}
	}

	private final CMMicroOp operation;
	private final Particle particle;
	private final ParticleId particleId;

	private CMMicroInstruction(CMMicroOp operation, Particle particle, ParticleId particleId) {
		this.operation = operation;
		this.particle = particle;
		this.particleId = particleId;
	}

	public CMMicroOp getMicroOp() {
		return operation;
	}

	public Particle getParticle() {
		return particle;
	}

	public ParticleId getParticleId() {
		return particleId;
	}

	public boolean isPush() {
		return operation.nextSpin != null;
	}

	public boolean isCheckSpin() {
		return operation.checkSpin != null;
	}

	public Spin getCheckSpin() {
		if (!isCheckSpin()) {
			throw new IllegalStateException(operation + " is not a check spin operation.");
		}

		return operation.checkSpin;
	}

	public Spin getNextSpin() {
		if (!isPush()) {
			throw new IllegalStateException(operation + " is not a push operation.");
		}

		return operation.nextSpin;
	}

	public static CMMicroInstruction spinDown(ParticleId particleId) {
		return new CMMicroInstruction(CMMicroOp.SPIN_DOWN, null, particleId);
	}

	public static CMMicroInstruction virtualSpinDown(Particle particle) {
		return new CMMicroInstruction(CMMicroOp.VIRTUAL_SPIN_DOWN, particle, null);
	}

	public static CMMicroInstruction spinUp(Particle particle) {
		return new CMMicroInstruction(CMMicroOp.SPIN_UP, particle, null);
	}

	public static CMMicroInstruction particleGroup() {
		return new CMMicroInstruction(CMMicroOp.PARTICLE_GROUP, null, null);
	}

	@Override
	public String toString() {
		return String.format("%s %s",
			operation,
			particle != null
				? HashUtils.sha256(DefaultSerialization.getInstance().toDson(particle, DsonOutput.Output.ALL)) + ":" + particle
				: particleId
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(operation, particle, particleId);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CMMicroInstruction)) {
			return false;
		}

		var other = (CMMicroInstruction) o;
		return Objects.equals(this.operation, other.operation)
			&& Objects.equals(this.particle, other.particle)
			&& Objects.equals(this.particleId, other.particleId);
	}
}
