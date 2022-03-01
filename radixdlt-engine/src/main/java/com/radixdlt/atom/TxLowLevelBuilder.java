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

package com.radixdlt.atom;

import static com.radixdlt.constraintmachine.REInstruction.REMicroOp.MSG;

import com.google.common.hash.HashCode;
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.system.state.SystemData;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.utils.Shorts;
import com.radixdlt.utils.UInt256;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Low level builder class for transactions */
public final class TxLowLevelBuilder {
  private final ByteArrayOutputStream blobStream;
  private final Map<SystemMapKey, LocalSubstate> localMapValues = new HashMap<>();
  private final Map<Integer, LocalSubstate> localUpSubstates = new HashMap<>();
  private final Set<SubstateId> remoteDownSubstate = new HashSet<>();
  private final SubstateSerialization serialization;
  private final OptionalInt maxMessageLen;
  private int upParticleCount = 0;

  private TxLowLevelBuilder(
      SubstateSerialization serialization,
      ByteArrayOutputStream blobStream,
      OptionalInt maxMessageLen) {
    maxMessageLen.ifPresent(this::validateMaxMessageLen);

    this.serialization = serialization;
    this.blobStream = blobStream;
    this.maxMessageLen = maxMessageLen;
  }

  private void validateMaxMessageLen(int limit) {
    if (limit > Short.MAX_VALUE) {
      throw new IllegalArgumentException(
          "Attempt to set limit which exceeds transaction format capabilities");
    }
  }

  public static TxLowLevelBuilder newBuilder(byte[] blob) {
    var blobStream = new ByteArrayOutputStream();
    try {
      blobStream.write(blob);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to write data.", e);
    }
    // TODO: Cleanup null serialization, but works for now as only used for client side signing
    return new TxLowLevelBuilder(null, blobStream, OptionalInt.empty());
  }

  public static TxLowLevelBuilder newBuilder(SubstateSerialization serialization) {
    return newBuilder(serialization, OptionalInt.empty());
  }

  public static TxLowLevelBuilder newBuilder(
      SubstateSerialization serialization, OptionalInt maxMessageLen) {
    return new TxLowLevelBuilder(serialization, new ByteArrayOutputStream(), maxMessageLen);
  }

  public Set<SubstateId> remoteDownSubstate() {
    return remoteDownSubstate;
  }

  public Optional<LocalSubstate> get(SystemMapKey mapKey) {
    return Optional.ofNullable(localMapValues.get(mapKey));
  }

  public List<LocalSubstate> localUpSubstate() {
    return new ArrayList<>(localUpSubstates.values());
  }

  private void instruction(REInstruction.REMicroOp op, ByteBuffer buffer) {
    blobStream.write(op.opCode());
    blobStream.write(buffer.array(), buffer.position(), buffer.remaining());
  }

  public void instruction(REInstruction.REMicroOp op, byte[] data) {
    blobStream.write(op.opCode());
    try {
      blobStream.write(data);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to write data.", e);
    }
  }

  TxLowLevelBuilder message(byte[] bytes) throws MessageTooLongException {
    var limit = getMessageLengthLimit();

    if (bytes.length > limit) {
      throw new MessageTooLongException(limit, bytes.length);
    }

    var buf = ByteBuffer.allocate(Short.BYTES + bytes.length);
    buf.putShort((short) bytes.length);
    buf.put(bytes);

    instruction(MSG, buf.array());
    return this;
  }

  private int getMessageLengthLimit() {
    return maxMessageLen.orElseThrow(
        () ->
            new IllegalStateException(
                "Attempt to add message without providing message length limit"));
  }

  public TxLowLevelBuilder up(Particle substate) {
    Objects.requireNonNull(substate, "substate is required");

    var localSubstate = LocalSubstate.create(upParticleCount, substate);

    if (substate instanceof ValidatorData validatorData) {
      var b = serialization.classToByte(validatorData.getClass());
      var k = SystemMapKey.ofSystem(b, validatorData.validatorKey().getCompressedBytes());
      this.localMapValues.put(k, localSubstate);
    } else if (substate instanceof SystemData) {
      var b = serialization.classToByte(substate.getClass());
      var k = SystemMapKey.ofSystem(b);
      this.localMapValues.put(k, localSubstate);
    } else if (substate instanceof VirtualParent virtualParent) {
      var typeByte = virtualParent.data()[0];
      var k = SystemMapKey.ofSystem(typeByte);
      this.localMapValues.put(k, localSubstate);
    }

    this.localUpSubstates.put(upParticleCount, localSubstate);

    var buf = ByteBuffer.allocate(1024);
    buf.putShort((short) 0);
    serialization.serialize(substate, buf);
    var limit = buf.position();
    buf.putShort(0, (short) (limit - 2));
    buf.position(0);
    buf.limit(limit);
    instruction(REInstruction.REMicroOp.UP, buf);
    upParticleCount++;
    return this;
  }

  public TxLowLevelBuilder localVirtualDown(int index, byte[] virtualKey) {
    validateVirtualKey(virtualKey);

    var buf = ByteBuffer.allocate(Short.BYTES + Short.BYTES + virtualKey.length);
    buf.putShort((short) (virtualKey.length + Short.BYTES));
    buf.putShort((short) index);
    buf.put(virtualKey);
    instruction(REInstruction.REMicroOp.LVDOWN, buf.array());
    return this;
  }

  public TxLowLevelBuilder localVirtualRead(int index, byte[] virtualKey) {
    validateVirtualKey(virtualKey);

    var buf = ByteBuffer.allocate(Short.BYTES + Short.BYTES + virtualKey.length);
    buf.putShort((short) (virtualKey.length + Short.BYTES));
    buf.putShort((short) index);
    buf.put(virtualKey);
    instruction(REInstruction.REMicroOp.LVREAD, buf.array());
    return this;
  }

  public TxLowLevelBuilder virtualDown(SubstateId parent, byte[] virtualKey) {
    validateVirtualKey(virtualKey);

    var id = SubstateId.ofVirtualSubstate(parent, virtualKey);
    var buf = ByteBuffer.allocate(Short.BYTES + id.asBytes().length);
    buf.putShort((short) id.asBytes().length);
    buf.put(id.asBytes());
    instruction(REInstruction.REMicroOp.VDOWN, buf.array());
    return this;
  }

  public TxLowLevelBuilder virtualRead(SubstateId parent, byte[] virtualKey) {
    validateVirtualKey(virtualKey);

    var id = SubstateId.ofVirtualSubstate(parent, virtualKey);
    var buf = ByteBuffer.allocate(Short.BYTES + id.asBytes().length);
    buf.putShort((short) id.asBytes().length);
    buf.put(id.asBytes());
    instruction(REInstruction.REMicroOp.VREAD, buf.array());
    return this;
  }

  private void validateVirtualKey(byte[] virtualKey) {
    if (virtualKey.length > 128) {
      throw new IllegalStateException("Virtual key length > 128");
    }
  }

  public TxLowLevelBuilder localRead(int index) {
    validateSubstatePresentAtIndex(localUpSubstates.get(index), index);
    instruction(REInstruction.REMicroOp.LREAD, Shorts.toByteArray((short) index));
    return this;
  }

  public TxLowLevelBuilder read(SubstateId substateId) {
    instruction(REInstruction.REMicroOp.READ, substateId.asBytes());
    return this;
  }

  public TxLowLevelBuilder localDown(int index) {
    validateSubstatePresentAtIndex(localUpSubstates.remove(index), index);
    instruction(REInstruction.REMicroOp.LDOWN, Shorts.toByteArray((short) index));
    return this;
  }

  private void validateSubstatePresentAtIndex(LocalSubstate substate, int index) {
    if (substate == null) {
      throw new IllegalStateException("Local substate does not exist at index : " + index);
    }
  }

  public TxLowLevelBuilder down(SubstateId substateId) {
    instruction(REInstruction.REMicroOp.DOWN, substateId.asBytes());
    this.remoteDownSubstate.add(substateId);
    return this;
  }

  public TxLowLevelBuilder readIndex(SubstateIndex<?> index) {
    var buf = ByteBuffer.allocate(Short.BYTES + index.getPrefix().length);
    buf.putShort((short) index.getPrefix().length);
    buf.put(index.getPrefix());
    instruction(REInstruction.REMicroOp.READINDEX, buf.array());
    return this;
  }

  public TxLowLevelBuilder downIndex(SubstateIndex<?> index) {
    var buf = ByteBuffer.allocate(Short.BYTES + index.getPrefix().length);
    buf.putShort((short) index.getPrefix().length);
    buf.put(index.getPrefix());
    instruction(REInstruction.REMicroOp.DOWNINDEX, buf.array());
    return this;
  }

  public TxLowLevelBuilder end() {
    instruction(REInstruction.REMicroOp.END, new byte[0]);
    return this;
  }

  public TxLowLevelBuilder syscall(Syscall syscall, byte[] bytes) {
    if (bytes.length < 1 || bytes.length > 32) {
      throw new IllegalStateException("Length must be >= 1 and <= 32 but was " + bytes.length);
    }
    var data = new byte[Short.BYTES + 1 + bytes.length];
    data[0] = 0;
    data[1] = (byte) (bytes.length + 1);
    data[2] = syscall.id();
    System.arraycopy(bytes, 0, data, 3, bytes.length);
    instruction(REInstruction.REMicroOp.SYSCALL, data);
    return this;
  }

  public TxLowLevelBuilder syscall(Syscall syscall, UInt256 amount) {
    var data = new byte[Short.BYTES + 1 + UInt256.BYTES];
    data[0] = 0;
    data[1] = UInt256.BYTES + 1;
    data[2] = syscall.id();
    amount.toByteArray(data, 3);
    instruction(REInstruction.REMicroOp.SYSCALL, data);
    return this;
  }

  public TxLowLevelBuilder disableResourceAllocAndDestroy() {
    var data = new byte[] {0, 1};
    instruction(REInstruction.REMicroOp.HEADER, data);
    return this;
  }

  public int size() {
    return blobStream.size();
  }

  public byte[] blob() {
    return blobStream.toByteArray();
  }

  public HashCode hashToSign() {
    return HashUtils.sha256(blob()); // this is a double hash
  }

  public TxLowLevelBuilder sig(ECDSASignature signature) {
    instruction(REInstruction.REMicroOp.SIG, REFieldSerialization.serializeSignature(signature));
    return this;
  }

  public Txn build() {
    return Txn.create(blobStream.toByteArray());
  }
}
