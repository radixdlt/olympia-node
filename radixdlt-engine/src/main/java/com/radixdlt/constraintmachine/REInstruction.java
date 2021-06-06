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

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.RESerializer;
import com.radixdlt.atom.Txn;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Unparsed Low level instruction into Radix Engine
 */
public final class REInstruction {
	private interface ReadData {
		Object read(Txn txn, int i, ByteBuffer buf) throws DeserializeException;
	}

	public enum REMicroOp {
		UP((byte) 1, (txn, i, b) -> {
			var p = RESerializer.deserialize(b);
			return Substate.create(p, SubstateId.ofSubstate(txn.getId(), i));
		}, REOp.UP),
		VDOWN((byte) 2, (txn, i, b) -> {
			int pos = b.position();
			var p = RESerializer.deserialize(b);
			int length = b.position() - pos;
			var buf = ByteBuffer.wrap(b.array(), pos, length);
			return Substate.create(p, SubstateId.ofVirtualSubstate(buf));
		}, REOp.DOWN),
		VDOWNARG((byte) 3, (txn, i, b) -> {
			int pos = b.position();
			var p = RESerializer.deserialize(b);
			int length = b.position() - pos;
			var buf = ByteBuffer.wrap(b.array(), pos, length);
			var argLength = b.get();
			var arg = new byte[Byte.toUnsignedInt(argLength)];
			b.get(arg);
			return Pair.of(Substate.create(p, SubstateId.ofVirtualSubstate(buf)), arg);
		}, REOp.DOWN),
		DOWN((byte) 4, (txn, i, b) -> SubstateId.fromBuffer(b), REOp.DOWN),
		LDOWN((byte) 5, (txn, i, b) -> {
			var index = b.getInt();
			if (index < 0 || index >= i) {
				throw new DeserializeException("Bad local index: " + index);
			}
			return SubstateId.ofSubstate(txn.getId(), index);
		}, REOp.DOWN),
		DOWNALL((byte) 8, (txn, i, b) -> {
			var classId = b.get();
			return RESerializer.byteToClass(classId); // Just to check to make sure classId exists
		}, REOp.DOWNALL),
		MSG((byte) 6, (txn, i, b) -> {
			var length = Byte.toUnsignedInt(b.get());
			var bytes = new byte[length];
			b.get(bytes);
			return bytes;
		}, REOp.MSG),
		SIG((byte) 7, (txn, i, b) -> {
			return RESerializer.deserializeSignature(b);
		}, REOp.SIG),
		END((byte) 0, (txn, i, b) -> null, REOp.END);

		private final ReadData readData;
		private final REOp op;
		private final byte opCode;

		REMicroOp(byte opCode, ReadData readData, REOp op) {
			this.opCode = opCode;
			this.readData =  readData;
			this.op = op;
		}

		public REOp getOp() {
			return op;
		}

		public Object readData(Txn txn, int index, ByteBuffer buf) throws DeserializeException {
			try {
				return readData.read(txn, index, buf);
			} catch (BufferUnderflowException e) {
				throw new DeserializeException("Buffer underflow @" + index, e);
			}
		}

		public byte opCode() {
			return opCode;
		}

		static REMicroOp fromByte(byte op) throws DeserializeException {
			for (var microOp : REMicroOp.values()) {
				if (microOp.opCode == op) {
					return microOp;
				}
			}

			throw new DeserializeException("Unknown opcode: " + op);
		}
	}

	private final REMicroOp microOp;
	private final Object data;
	private final byte[] array;
	private final int offset;
	private final int length;

	private REInstruction(REMicroOp microOp, Object data, byte[] array, int offset, int length) {
		this.microOp = microOp;
		this.data = data;
		this.array = array;
		this.offset = offset;
		this.length = length;
	}

	public REMicroOp getMicroOp() {
		return microOp;
	}

	public ByteBuffer getDataByteBuffer() {
		return ByteBuffer.wrap(array, offset, length);
	}

	public <T> T getData() {
		return (T) data;
	}

	public boolean isStateUpdate() {
		return microOp.op.isSubstateUpdate();
	}

	public static REInstruction readFrom(Txn txn, int index, ByteBuffer buf) throws DeserializeException {
		var microOp = REMicroOp.fromByte(buf.get());
		var pos = buf.position();
		var data = microOp.readData(txn, index, buf);
		var length = buf.position() - pos;
		return new REInstruction(microOp, data, buf.array(), pos, length);
	}

	@Override
	public String toString() {
		return String.format("%s %s", microOp, data);
	}
}
