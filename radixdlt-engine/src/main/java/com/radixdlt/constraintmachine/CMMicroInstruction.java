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

import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;
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

		static CMMicroOp fromByte(byte op) {
			for (var microOp : CMMicroOp.values()) {
				if (microOp.opCode == op) {
					return microOp;
				}
			}

			throw new IllegalArgumentException("Unknown opcode: " + op);
		}
	}

	private final CMMicroOp operation;
	private final byte[] data;

	private CMMicroInstruction(CMMicroOp operation, byte[] data) {
		this.operation = operation;
		this.data = data;
	}

	public CMMicroOp getMicroOp() {
		return operation;
	}

	public byte[] getData() {
		return data;
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

	public static CMMicroInstruction create(byte op, byte[] data) {
		var microOp = CMMicroOp.fromByte(op);
		return new CMMicroInstruction(microOp, data);
	}

	public static CMMicroInstruction particleGroup() {
		return new CMMicroInstruction(CMMicroOp.PARTICLE_GROUP, new byte[0]);
	}

	@Override
	public String toString() {
		return String.format("%s %s",
			operation,
			Hex.toHexString(data)
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(operation, Arrays.hashCode(data));
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CMMicroInstruction)) {
			return false;
		}

		var other = (CMMicroInstruction) o;
		return Objects.equals(this.operation, other.operation)
			&& Arrays.equals(this.data, other.data);
	}
}
