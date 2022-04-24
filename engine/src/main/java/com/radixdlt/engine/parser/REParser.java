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

package com.radixdlt.engine.parser;

import com.google.common.hash.HashCode;
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.CallData;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.REOp;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.exceptions.CallDataAccessException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.parser.exceptions.TrailingBytesException;
import com.radixdlt.engine.parser.exceptions.TxnParseException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class REParser {
  private final SubstateDeserialization substateDeserialization;

  public REParser(SubstateDeserialization substateDeserialization) {
    this.substateDeserialization = substateDeserialization;
  }

  public SubstateDeserialization getSubstateDeserialization() {
    return substateDeserialization;
  }

  public static class ParserState {
    private final Txn txn;
    private final List<REInstruction> instructions = new ArrayList<>();
    private byte[] msg = null;
    private int upSubstateCount = 0;
    private int substateUpdateCount = 0;
    private int endCount = 0;
    private int position = 0;
    private boolean disableResourceAllocAndDestroy = false;

    ParserState(Txn txn) {
      this.txn = txn;
    }

    public List<REInstruction> instructions() {
      return instructions;
    }

    void header(boolean disableResourceAllocAndDestroy) throws TxnParseException {
      if (instructions.size() != 1) {
        throw new TxnParseException(this, "Header must be first");
      }
      this.disableResourceAllocAndDestroy = disableResourceAllocAndDestroy;
    }

    void read() {}

    void pos(int curPos) {
      this.position = curPos;
    }

    public int curPosition() {
      return this.position;
    }

    void nextInstruction(REInstruction inst) {
      instructions.add(inst);
    }

    public int curIndex() {
      return instructions.size();
    }

    public AID txnId() {
      return txn.getId();
    }

    public int upSubstateCount() {
      return upSubstateCount;
    }

    void msg(byte[] msg) throws TxnParseException {
      if (this.msg != null) {
        throw new TxnParseException(this, "Too many messages");
      }
      this.msg = msg;
    }

    void substateUpdate(REOp op) {
      substateUpdateCount++;
      if (op == REOp.UP) {
        upSubstateCount++;
      }
    }

    void end() throws TxnParseException {
      if (substateUpdateCount == 0) {
        throw new TxnParseException(this, "Empty group");
      }
      endCount++;
      substateUpdateCount = 0;
    }

    void finish() throws TxnParseException {
      if (substateUpdateCount != 0) {
        throw new TxnParseException(this, "Missing end");
      }

      if (endCount == 0) {
        throw new TxnParseException(this, "No state updates");
      }
    }
  }

  public ParsedTxn parse(Txn txn) throws TxnParseException {
    UInt256 feePaid = null;
    ECDSASignature sig = null;
    int sigPosition = 0;
    var parserState = new ParserState(txn);

    var buf = ByteBuffer.wrap(txn.getPayload());
    while (buf.hasRemaining()) {
      if (sig != null) {
        throw new TxnParseException(parserState, "Signature must be last");
      }

      var curPos = buf.position();
      parserState.pos(curPos);
      final var inst = readInstruction(parserState, buf);
      parserState.nextInstruction(inst);

      if (inst.isStateUpdate()) {
        parserState.substateUpdate(inst.getMicroOp().getOp());
      } else if (inst.getMicroOp().getOp() == REOp.READ
          || inst.getMicroOp().getOp() == REOp.READINDEX) {
        parserState.read();
      } else if (inst.getMicroOp() == REInstruction.REMicroOp.HEADER) {
        parserState.header(inst.getData());
      } else if (inst.getMicroOp() == REInstruction.REMicroOp.SYSCALL) {
        try {
          var callData = inst.<CallData>getData();
          byte id = callData.get(0);
          var syscall =
              Syscall.of(id)
                  .orElseThrow(
                      () -> new TxnParseException(parserState, "Invalid call data type: " + id));

          switch (syscall) {
            case FEE_RESERVE_PUT:
              if (feePaid != null) {
                throw new TxnParseException(parserState, "Should only pay fees once.");
              }
              feePaid = callData.getUInt256(1);
              break;
            case FEE_RESERVE_TAKE:
              if (feePaid == null) {
                throw new TxnParseException(parserState, "No fees paid");
              }
              var takeAmount = callData.getUInt256(1);
              if (takeAmount.compareTo(feePaid) > 0) {
                throw new TxnParseException(parserState, "Trying to take more fees than paid");
              }
              feePaid = feePaid.subtract(takeAmount);
              break;
            case READDR_CLAIM:
              break;
              // TODO: Need to rethink how stateless verification occurs here
              // TODO: Along with FeeConstraintScrypt.java
            default:
              throw new TxnParseException(parserState, "Invalid call data type: " + id);
          }
        } catch (CallDataAccessException | TrailingBytesException e) {
          throw new TxnParseException(parserState, e);
        }
      } else if (inst.getMicroOp() == REInstruction.REMicroOp.MSG) {
        parserState.msg(inst.getData());
      } else if (inst.getMicroOp() == REInstruction.REMicroOp.END) {
        parserState.end();
      } else if (inst.getMicroOp() == REInstruction.REMicroOp.SIG) {
        sigPosition = curPos;
        sig = inst.getData();
      } else {
        throw new TxnParseException(parserState, "Unknown CM Op " + inst.getMicroOp());
      }
    }

    parserState.finish();

    return new ParsedTxn(
        txn,
        feePaid,
        parserState.instructions,
        parserState.msg,
        sig == null ? null : Pair.of(calculatePayloadHash(txn, sigPosition), sig),
        parserState.disableResourceAllocAndDestroy);
  }

  private HashCode calculatePayloadHash(Txn txn, int sigPosition) {
    return HashUtils.sha256(txn.getPayload(), 0, sigPosition); // This is a double hash
  }

  private REInstruction readInstruction(ParserState parserState, ByteBuffer buf)
      throws TxnParseException {
    try {
      return REInstruction.readFrom(parserState, buf);
    } catch (Exception e) {
      throw new TxnParseException(parserState, "Could not read instruction", e);
    }
  }
}
