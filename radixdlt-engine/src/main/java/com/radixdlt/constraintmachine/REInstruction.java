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
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Unparsed Low level instruction into Radix Engine
 */
public final class REInstruction {
	public enum REMicroOp {
		END((byte) 0x0, REOp.END) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer b, SubstateDeserialization d) throws DeserializeException {
				return null;
			}
		},
		SYSCALL((byte) 0x1, REOp.SYSCALL) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer b, SubstateDeserialization d) throws DeserializeException {
				int bufSize = Byte.toUnsignedInt(b.get());
				// TODO: Remove buffer copy
				var callData = new byte[bufSize];
				b.get(callData);
				return new CallData(callData);
			}
		},
		UP((byte) 0x2, REOp.UP) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException {
				var p = d.deserialize(buf);
				return Substate.create(p, SubstateId.ofSubstate(parserState.txnId(), parserState.upSubstateCount()));
			}
		},
		READ((byte) 0x3, REOp.READ) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException {
				return SubstateId.fromBuffer(buf);
			}
		},
		LREAD((byte) 0x4, REOp.READ) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException {
				var index = buf.getInt();
				if (index < 0 || index >= parserState.upSubstateCount()) {
					throw new DeserializeException("Bad local index: " + index);
				}
				return SubstateId.ofSubstate(parserState.txnId(), index);
			}
		},
		VREAD((byte) 0x5, REOp.READ) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException {
				int pos = buf.position();
				var p = d.deserialize(buf);
				int length = buf.position() - pos;
				var b = ByteBuffer.wrap(buf.array(), pos, length);
				return Substate.create(p, SubstateId.ofVirtualSubstate(b));
			}
		},
		DOWN((byte) 0x6, REOp.DOWN) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException {
				return SubstateId.fromBuffer(buf);
			}
		},
		LDOWN((byte) 0x7, REOp.DOWN) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException {
				var index = buf.getInt();
				if (index < 0 || index >= parserState.upSubstateCount()) {
					throw new DeserializeException("Bad local index: " + index);
				}
				return SubstateId.ofSubstate(parserState.txnId(), index);
			}
		},
		VDOWN((byte) 0x8, REOp.DOWN) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException {
				int pos = buf.position();
				var p = d.deserialize(buf);
				int length = buf.position() - pos;
				var b = ByteBuffer.wrap(buf.array(), pos, length);
				return Substate.create(p, SubstateId.ofVirtualSubstate(b));
			}
		},
		VDOWNARG((byte) 0x9, REOp.DOWN) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException {
				int pos = buf.position();
				var p = d.deserialize(buf);
				int length = buf.position() - pos;
				var b = ByteBuffer.wrap(buf.array(), pos, length);
				var argLength = buf.get();
				var arg = new byte[Byte.toUnsignedInt(argLength)];
				buf.get(arg);
				return Pair.of(Substate.create(p, SubstateId.ofVirtualSubstate(b)), arg);
			}
		},
		DOWNINDEX((byte) 0xa, REOp.DOWNINDEX) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer b, SubstateDeserialization d) throws DeserializeException {
				int indexSize = Byte.toUnsignedInt(b.get());
				if (indexSize <= 0 || indexSize > 10) {
					throw new DeserializeException("Bad DownIndex size " + indexSize);
				}
				var buf = new byte[indexSize];
				b.get(buf);
				return SubstateIndex.create(buf, d.byteToClass(buf[0]));
			}
		},
		SIG((byte) 0xb, REOp.SIG) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer b, SubstateDeserialization d) throws DeserializeException {
				return REFieldSerialization.deserializeSignature(b);
			}
		},
		MSG((byte) 0xc, REOp.MSG) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer b, SubstateDeserialization d) throws DeserializeException {
				var length = Byte.toUnsignedInt(b.get());
				var bytes = new byte[length];
				b.get(bytes);
				return bytes;
			}
		},
		HEADER((byte) 0xd, REOp.HEADER) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer b, SubstateDeserialization d) throws DeserializeException {
				int version = b.get();
				if (version != 0) {
					throw new DeserializeException("Version must be 0");
				}
				var flags = b.get();
				if ((flags & 0xe) != 0) {
					throw new DeserializeException("Invalid flags");
				}
				return (flags & 0x1) == 1;
			}
		};

		private final REOp op;
		private final byte opCode;

		REMicroOp(byte opCode, REOp op) {
			this.opCode = opCode;
			this.op = op;
		}

		public REOp getOp() {
			return op;
		}

		abstract Object read(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization d) throws DeserializeException;

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

	public static REInstruction readFrom(REParser.ParserState parserState, ByteBuffer buf, SubstateDeserialization deserialization)
		throws DeserializeException {
		try {
			var microOp = REMicroOp.fromByte(buf.get());
			var pos = buf.position();
			var data = microOp.read(parserState, buf, deserialization);
			var length = buf.position() - pos;
			return new REInstruction(microOp, data, buf.array(), pos, length);
		} catch (BufferUnderflowException e) {
			throw new DeserializeException("Buffer underflow @" + parserState.curIndex(), e);
		}
	}

	@Override
	public String toString() {
		return String.format("%s %s", microOp, data);
	}
}
