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
	private enum LengthType {
		FIXED {
			@Override
			void verifyLimits(int min, int max) {
				if (min != max) {
					throw new IllegalStateException();
				}
			}

			@Override
			ByteBuffer setNextLimit(ByteBuffer buf, int min, int max) {
				return buf.limit(buf.position() + max);
			}
		},
		VARIABLE {
			@Override
			void verifyLimits(int min, int max) {
				if (min >= max) {
					throw new IllegalStateException();
				}
			}

			@Override
			ByteBuffer setNextLimit(ByteBuffer buf, int min, int max) throws DeserializeException {
				int size = REFieldSerialization.deserializeUnsignedShort(buf, min, max);
				return buf.limit(buf.position() + size);
			}
		};

		abstract void verifyLimits(int min, int max);

		abstract ByteBuffer setNextLimit(ByteBuffer buf, int min, int max) throws DeserializeException;
	}

	public enum REMicroOp {
		END((byte) 0x0, REOp.END, LengthType.FIXED, 0, 0) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer buf) {
				return null;
			}
		},
		SYSCALL((byte) 0x1, REOp.SYSCALL, LengthType.VARIABLE, 0, 512) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer buf) {
				// TODO: Remove buffer copy
				var callData = new byte[buf.remaining()];
				buf.get(callData, 0, buf.remaining());
				return new CallData(callData);
			}
		},
		UP((byte) 0x2, REOp.UP, LengthType.VARIABLE, 2, 512) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var substateId = SubstateId.ofSubstate(parserState.txnId(), parserState.upSubstateCount());
				var start = buf.position();
				buf.position(start + buf.remaining());
				return new UpSubstate(substateId, buf.array(), start, buf.limit() - start);
			}
		},
		READ((byte) 0x3, REOp.READ, LengthType.FIXED, SubstateId.BYTES, SubstateId.BYTES) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				return SubstateId.fromBuffer(buf);
			}
		},
		LREAD((byte) 0x4, REOp.READ, LengthType.FIXED, Short.BYTES, Short.BYTES) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				int index = REFieldSerialization.deserializeUnsignedShort(buf, 0, parserState.upSubstateCount() - 1);
				return SubstateId.ofSubstate(parserState.txnId(), index);
			}
		},
		VREAD((byte) 0x5, REOp.READ, LengthType.VARIABLE, SubstateId.BYTES + 1, 512) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var bytes = new byte[buf.remaining()];
				buf.get(bytes, 0, buf.remaining());
				return SubstateId.fromBytes(bytes);
			}
		},
		LVREAD((byte) 0x6, REOp.READ, LengthType.VARIABLE, Short.BYTES + 1, 512) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var index = REFieldSerialization.deserializeUnsignedShort(buf, 0, parserState.upSubstateCount() - 1);
				var parent = SubstateId.ofSubstate(parserState.txnId(), index);
				var bytes = new byte[buf.remaining()];
				buf.get(bytes, 0, buf.remaining());
				return SubstateId.ofVirtualSubstate(parent, bytes);
			}
		},
		DOWN((byte) 0x7, REOp.DOWN, LengthType.FIXED, SubstateId.BYTES, SubstateId.BYTES) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				return SubstateId.fromBuffer(buf);
			}
		},
		LDOWN((byte) 0x8, REOp.DOWN, LengthType.FIXED, Short.BYTES, Short.BYTES) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var index = REFieldSerialization.deserializeUnsignedShort(buf, 0, parserState.upSubstateCount() - 1);
				return SubstateId.ofSubstate(parserState.txnId(), index);
			}
		},
		VDOWN((byte) 0x9, REOp.DOWN, LengthType.VARIABLE, SubstateId.BYTES + 1, 512) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var bytes = new byte[buf.remaining()];
				buf.get(bytes, 0, buf.remaining());
				return SubstateId.fromBytes(bytes);
			}
		},
		LVDOWN((byte) 0xa, REOp.DOWN, LengthType.VARIABLE, Short.BYTES + 1, 512) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var index = REFieldSerialization.deserializeUnsignedShort(buf, 0, parserState.upSubstateCount() - 1);
				var parent = SubstateId.ofSubstate(parserState.txnId(), index);
				var bytes = new byte[buf.remaining()];
				buf.get(bytes);
				return SubstateId.ofVirtualSubstate(parent, bytes);
			}
		},
		SIG((byte) 0xb, REOp.SIG, LengthType.FIXED, 1 + 32 + 32, 1 + 32 + 32) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer b) {
				return REFieldSerialization.deserializeSignature(b);
			}
		},
		MSG((byte) 0xc, REOp.MSG, LengthType.VARIABLE, 1, 255) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) throws DeserializeException {
				var bytes = new byte[buf.remaining()];
				buf.get(bytes);
				return bytes;
			}
		},
		HEADER((byte) 0xd, REOp.HEADER, LengthType.FIXED, 2, 2) {
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
		READINDEX((byte) 0xe, REOp.READINDEX, LengthType.VARIABLE, 1, 64) {
			@Override
			Object read(REParser.ParserState parserState, ByteBuffer buf) {
				var bytes = new byte[buf.remaining()];
				buf.get(bytes);
				return bytes;
			}
		},
		DOWNINDEX((byte) 0xf, REOp.DOWNINDEX, LengthType.VARIABLE, 1, 64) {
			@Override
			public Object read(REParser.ParserState parserState, ByteBuffer buf) {
				var bytes = new byte[buf.remaining()];
				buf.get(bytes);
				return bytes;
			}
		};

		private final REOp op;
		private final byte opCode;
		private final LengthType lengthType;
		private final int minLength;
		private final int maxLength;

		REMicroOp(byte opCode, REOp op, LengthType lengthType, int minLength, int maxLength) {
			// Sanity checks
			lengthType.verifyLimits(minLength, maxLength);

			this.opCode = opCode;
			this.op = op;
			this.lengthType = lengthType;
			this.minLength = minLength;
			this.maxLength = maxLength;
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
		var savedLimit = buf.limit();
		var pos = buf.position();
		try {
			// Set limit
			buf = microOp.lengthType.setNextLimit(buf, microOp.minLength, microOp.maxLength);

			var data = microOp.read(parserState, buf);

			// Sanity check
			if (buf.hasRemaining()) {
				throw new IllegalStateException();
			}

			var length = buf.position() - pos;
			buf.limit(savedLimit);
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
