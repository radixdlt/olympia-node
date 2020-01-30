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

public final class CMMicroInstruction {
	public enum CMMicroOp {
		CHECK_NEUTRAL,
		CHECK_UP,
		PUSH,
		PARTICLE_GROUP
	}

	private final CMMicroOp operation;
	private final Particle particle;

	private CMMicroInstruction(CMMicroOp operation, Particle particle) {
		this.operation = operation;
		this.particle = particle;
	}

	public CMMicroOp getMicroOp() {
		return operation;
	}

	public Particle getParticle() {
		return particle;
	}

	public boolean isCheckSpin() {
		return operation == CMMicroOp.CHECK_NEUTRAL || operation == CMMicroOp.CHECK_UP;
	}

	public Spin getCheckSpin() {
		if (operation == CMMicroOp.CHECK_NEUTRAL) {
			return Spin.NEUTRAL;
		} else if (operation == CMMicroOp.CHECK_UP) {
			return Spin.UP;
		} else {
			throw new UnsupportedOperationException(operation + " is not a check spin operation.");
		}
	}

	public static CMMicroInstruction push(Particle particle) {
		return new CMMicroInstruction(CMMicroOp.PUSH, particle);
	}

	public static CMMicroInstruction checkSpin(Particle particle, Spin spin) {
		if (spin == Spin.NEUTRAL) {
			return new CMMicroInstruction(CMMicroOp.CHECK_NEUTRAL, particle);
		} else if (spin == Spin.UP) {
			return new CMMicroInstruction(CMMicroOp.CHECK_UP, particle);
		} else {
			throw new IllegalStateException("Invalid check spin: " + spin);
		}
	}

	public static CMMicroInstruction particleGroup() {
		return new CMMicroInstruction(CMMicroOp.PARTICLE_GROUP, null);
	}
}
