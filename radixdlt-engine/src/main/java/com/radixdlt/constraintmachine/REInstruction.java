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

/**
 * Unparsed Low level instruction into Radix Engine
 */
public final class REInstruction {
	public enum REOp {
		UP((byte) 1, Spin.NEUTRAL, Spin.UP),
		VDOWN((byte) 2, Spin.UP, Spin.DOWN),
		DOWN((byte) 3, Spin.UP, Spin.DOWN),
		LDOWN((byte) 4, Spin.UP, Spin.DOWN),
		MSG((byte) 5, null, null),
		END((byte) 0, null, null);

		private final Spin checkSpin;
		private final Spin nextSpin;
		private final byte opCode;

		REOp(byte opCode, Spin checkSpin, Spin nextSpin) {
			this.opCode = opCode;
			this.checkSpin = checkSpin;
			this.nextSpin = nextSpin;
		}

		public byte opCode() {
			return opCode;
		}

		static REOp fromByte(byte op) {
			for (var microOp : REOp.values()) {
				if (microOp.opCode == op) {
					return microOp;
				}
			}

			throw new IllegalArgumentException("Unknown opcode: " + op);
		}
	}

	private final REOp operation;
	private final byte[] data;

	private REInstruction(REOp operation, byte[] data) {
		this.operation = operation;
		this.data = data;
	}

	public REOp getMicroOp() {
		return operation;
	}

	public byte[] getData() {
		return data;
	}

	public boolean isPush() {
		return operation.nextSpin != null;
	}

	public Spin getNextSpin() {
		if (!isPush()) {
			throw new IllegalStateException(operation + " is not a push operation.");
		}

		return operation.nextSpin;
	}

	public static REInstruction create(byte op, byte[] data) {
		var microOp = REOp.fromByte(op);
		return new REInstruction(microOp, data);
	}

	public static REInstruction particleGroup() {
		return new REInstruction(REOp.END, new byte[0]);
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
		if (!(o instanceof REInstruction)) {
			return false;
		}

		var other = (REInstruction) o;
		return Objects.equals(this.operation, other.operation)
			&& Arrays.equals(this.data, other.data);
	}
}
