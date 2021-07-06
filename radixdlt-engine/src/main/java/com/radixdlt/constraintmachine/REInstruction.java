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

import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.engine.parser.exceptions.REInstructionDataDeserializeException;
import com.radixdlt.serialization.DeserializeException;

import java.nio.ByteBuffer;

/**
 * Unparsed Low level instruction into Radix Engine
 */
public final class REInstruction {
	public enum REMicroOp {
		END((byte) 0x0, REOp.END) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer buf) {
				return null;
			}
		},
		SYSCALL((byte) 0x1, REOp.SYSCALL) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer buf) {
				int bufSize = Byte.toUnsignedInt(buf.get());
				// TODO: Remove buffer copy
				var callData = new byte[bufSize];
				buf.get(callData);
				return new CallData(callData);
			}
		},
		UP((byte) 0x2, REOp.UP) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				short size = buf.getShort();
				if (size < 0 || size > 1024) { // Arbitrary max size for now
					throw new DeserializeException("Invalid substate size: " + size);
				}
				var start = buf.position();
				var substateId = SubstateId.ofSubstate(parserState.txnId(), parserState.upSubstateCount());
				buf.position(start + size);
				return new UpSubstate(substateId, buf.array(), start, size);
			}
		},
		READ((byte) 0x3, REOp.READ) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				return SubstateId.fromBuffer(buf);
			}
		},
		LREAD((byte) 0x4, REOp.READ) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var index = buf.getInt();
				if (index < 0 || index >= parserState.upSubstateCount()) {
					throw new DeserializeException("Bad local index: " + index);
				}
				return SubstateId.ofSubstate(parserState.txnId(), index);
			}
		},
		VREAD((byte) 0x5, REOp.READ) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var length = Byte.toUnsignedInt(buf.get());
				if (length <= SubstateId.BYTES) {
					throw new DeserializeException("SubstateId is not virtual.");
				}
				var bytes = new byte[length];
				buf.get(bytes);
				return SubstateId.fromBytes(bytes);
			}
		},
		LVREAD((byte) 0x6, REOp.READ) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var length = Byte.toUnsignedInt(buf.get());
				if (length <= Integer.BYTES) {
					throw new DeserializeException("SubstateId is not virtual.");
				}
				var index = buf.getInt();
				if (index < 0 || index >= parserState.upSubstateCount()) {
					throw new DeserializeException("Bad local index: " + index);
				}
				var parent = SubstateId.ofSubstate(parserState.txnId(), index);
				var bytes = new byte[length - Integer.BYTES];
				buf.get(bytes);
				return SubstateId.ofVirtualSubstate(parent, bytes);
			}
		},
		DOWN((byte) 0x7, REOp.DOWN) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				return SubstateId.fromBuffer(buf);
			}
		},
		LDOWN((byte) 0x8, REOp.DOWN) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var index = buf.getInt();
				if (index < 0 || index >= parserState.upSubstateCount()) {
					throw new DeserializeException("Bad local index: " + index);
				}
				return SubstateId.ofSubstate(parserState.txnId(), index);
			}
		},
		VDOWN((byte) 0x9, REOp.DOWN) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var length = Byte.toUnsignedInt(buf.get());
				if (length <= SubstateId.BYTES) {
					throw new DeserializeException("SubstateId is not virtual.");
				}
				var bytes = new byte[length];
				buf.get(bytes);
				return SubstateId.fromBytes(bytes);
			}
		},
		LVDOWN((byte) 0xa, REOp.DOWN) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var length = Byte.toUnsignedInt(buf.get());
				if (length <= Integer.BYTES) {
					throw new DeserializeException("SubstateId is not virtual.");
				}
				var index = buf.getInt();
				if (index < 0 || index >= parserState.upSubstateCount()) {
					throw new DeserializeException("Bad local index: " + index);
				}
				var parent = SubstateId.ofSubstate(parserState.txnId(), index);
				var bytes = new byte[length - Integer.BYTES];
				buf.get(bytes);
				return SubstateId.ofVirtualSubstate(parent, bytes);
			}
		},
		SIG((byte) 0xb, REOp.SIG) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer b) throws DeserializeException {
				return REFieldSerialization.deserializeSignature(b);
			}
		},
		MSG((byte) 0xc, REOp.MSG) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var length = Byte.toUnsignedInt(buf.get());
				var bytes = new byte[length];
				buf.get(bytes);
				return bytes;
			}
		},
		HEADER((byte) 0xd, REOp.HEADER) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer b) throws DeserializeException {
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
		},
		READINDEX((byte) 0xe, REOp.READINDEX) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				int indexSize = Byte.toUnsignedInt(buf.get());
				var array = new byte[indexSize];
				buf.get(array);
				return array;
			}
		},
		DOWNINDEX((byte) 0xf, REOp.DOWNINDEX) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				int indexSize = Byte.toUnsignedInt(buf.get());
				var array = new byte[indexSize];
				buf.get(array);
				return array;
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

		abstract Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException;

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

	public int getDataLength() {
		return length;
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

	public static REInstruction readFrom(REParser.ParserState parserState, ByteBuffer buf)
		throws DeserializeException, REInstructionDataDeserializeException {

		var microOp = REMicroOp.fromByte(buf.get());
		var pos = buf.position();
		try {
			var data = microOp.read(parserState, buf);
			var length = buf.position() - pos;
			return new REInstruction(microOp, data, buf.array(), pos, length);
		} catch (Exception e) {
			throw new REInstructionDataDeserializeException(microOp, e);
		}
	}

	@Override
	public String toString() {
		return String.format("%s %s", microOp, data);
	}
}
