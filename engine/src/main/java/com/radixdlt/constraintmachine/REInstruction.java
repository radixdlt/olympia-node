/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.constraintmachine;

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.engine.parser.exceptions.REInstructionDataDeserializeException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bytes;
import java.nio.ByteBuffer;

/** Unparsed Low level instruction into Radix Engine */
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
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        var substateId = SubstateId.ofSubstate(parserState.txnId(), parserState.upSubstateCount());
        var start = buf.position();
        buf.position(start + buf.remaining());
        return new UpSubstate(substateId, buf.array(), start, buf.limit() - start);
      }
    },
    READ((byte) 0x3, REOp.READ, LengthType.FIXED, SubstateId.BYTES, SubstateId.BYTES) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        return SubstateId.fromBuffer(buf);
      }
    },
    LREAD((byte) 0x4, REOp.READ, LengthType.FIXED, Short.BYTES, Short.BYTES) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        int index =
            REFieldSerialization.deserializeUnsignedShort(
                buf, 0, parserState.upSubstateCount() - 1);
        return SubstateId.ofSubstate(parserState.txnId(), index);
      }
    },
    VREAD((byte) 0x5, REOp.READ, LengthType.VARIABLE, SubstateId.BYTES + 1, 512) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        var bytes = new byte[buf.remaining()];
        buf.get(bytes, 0, buf.remaining());
        return SubstateId.fromBytes(bytes);
      }
    },
    LVREAD((byte) 0x6, REOp.READ, LengthType.VARIABLE, Short.BYTES + 1, 512) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        var index =
            REFieldSerialization.deserializeUnsignedShort(
                buf, 0, parserState.upSubstateCount() - 1);
        var parent = SubstateId.ofSubstate(parserState.txnId(), index);
        var bytes = new byte[buf.remaining()];
        buf.get(bytes, 0, buf.remaining());
        return SubstateId.ofVirtualSubstate(parent, bytes);
      }
    },
    DOWN((byte) 0x7, REOp.DOWN, LengthType.FIXED, SubstateId.BYTES, SubstateId.BYTES) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        return SubstateId.fromBuffer(buf);
      }
    },
    LDOWN((byte) 0x8, REOp.DOWN, LengthType.FIXED, Short.BYTES, Short.BYTES) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        var index =
            REFieldSerialization.deserializeUnsignedShort(
                buf, 0, parserState.upSubstateCount() - 1);
        return SubstateId.ofSubstate(parserState.txnId(), index);
      }
    },
    VDOWN((byte) 0x9, REOp.DOWN, LengthType.VARIABLE, SubstateId.BYTES + 1, 512) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        var bytes = new byte[buf.remaining()];
        buf.get(bytes, 0, buf.remaining());
        return SubstateId.fromBytes(bytes);
      }
    },
    LVDOWN((byte) 0xa, REOp.DOWN, LengthType.VARIABLE, Short.BYTES + 1, 512) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
        var index =
            REFieldSerialization.deserializeUnsignedShort(
                buf, 0, parserState.upSubstateCount() - 1);
        var parent = SubstateId.ofSubstate(parserState.txnId(), index);
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return SubstateId.ofVirtualSubstate(parent, bytes);
      }
    },
    SIG((byte) 0xb, REOp.SIG, LengthType.FIXED, 1 + 32 + 32, 1 + 32 + 32) {
      @Override
      Object read(REParser.ParserState parserState, ByteBuffer b) throws DeserializeException {
        return REFieldSerialization.deserializeSignature(b);
      }
    },
    MSG((byte) 0xc, REOp.MSG, LengthType.VARIABLE, 1, 255) {
      @Override
      public Object read(REParser.ParserState parserState, ByteBuffer buf)
          throws DeserializeException {
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

    public int maxLength() {
      return maxLength;
    }

    public REOp getOp() {
      return op;
    }

    abstract Object read(REParser.ParserState parserState, ByteBuffer buf)
        throws DeserializeException;

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

  private static Object dataString(Object data) {
    if (data instanceof byte[]) {
      return Bytes.toHexString((byte[]) data);
    } else {
      return data;
    }
  }

  @Override
  public String toString() {
    return String.format("%s %s", microOp, dataString(data));
  }
}
