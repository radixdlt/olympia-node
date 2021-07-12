/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atom;

import com.google.common.hash.HashCode;
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Particle;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Low level builder class for transactions
 */
public final class TxLowLevelBuilder {
	private final ByteArrayOutputStream blobStream;
	private final Map<SystemMapKey, LocalSubstate> localMapValues = new HashMap<>();
	private final Map<Integer, LocalSubstate> localUpParticles = new HashMap<>();
	private final Set<SubstateId> remoteDownSubstate = new HashSet<>();
	private final SubstateSerialization serialization;
	private int upParticleCount = 0;

	TxLowLevelBuilder(SubstateSerialization serialization, ByteArrayOutputStream blobStream) {
		this.serialization = serialization;
		this.blobStream = blobStream;
	}

	public static TxLowLevelBuilder newBuilder(byte[] blob) {
		var blobStream = new ByteArrayOutputStream();
		try {
			blobStream.write(blob);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write data.", e);
		}
		// TODO: Cleanup null serialization, but works for now as only used for client side signing
		return new TxLowLevelBuilder(null, blobStream);
	}

	public static TxLowLevelBuilder newBuilder(SubstateSerialization serialization) {
		return new TxLowLevelBuilder(serialization, new ByteArrayOutputStream());
	}

	public Set<SubstateId> remoteDownSubstate() {
		return remoteDownSubstate;
	}

	public Optional<LocalSubstate> get(SystemMapKey mapKey) {
		return Optional.ofNullable(localMapValues.get(mapKey));
	}

	public List<LocalSubstate> localUpSubstate() {
		return new ArrayList<>(localUpParticles.values());
	}

	// TODO: Remove array copies
	private byte[] varLengthData(byte[] bytes) {
		if (bytes.length > 255) {
			throw new IllegalArgumentException("Data length is " + bytes.length + " but must be <= " + 255);
		}
		var data = new byte[Short.BYTES + bytes.length];
		data[0] = 0;
		data[1] = (byte) bytes.length;
		System.arraycopy(bytes, 0, data, 2, bytes.length);
		return data;
	}

	private void instruction(REInstruction.REMicroOp op, ByteBuffer buffer) {
		blobStream.write(op.opCode());
		blobStream.write(buffer.array(), buffer.position(), buffer.remaining());
	}

	private void instruction(REInstruction.REMicroOp op, byte[] data) {
		blobStream.write(op.opCode());
		try {
			blobStream.write(data);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write data.", e);
		}
	}

	public TxLowLevelBuilder message(byte[] bytes) {
		instruction(REInstruction.REMicroOp.MSG, varLengthData(bytes));
		return this;
	}

	public TxLowLevelBuilder message(String message) {
		var bytes = message.getBytes(StandardCharsets.UTF_8);
		return message(bytes);
	}

	public TxLowLevelBuilder up(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");

		var localSubstate = LocalSubstate.create(upParticleCount, particle);

		if (particle instanceof ValidatorData) {
			var p = (ValidatorData) particle;
			var b = serialization.classToByte(p.getClass());
			var k = SystemMapKey.ofValidatorData(b, p.getValidatorKey().getCompressedBytes());
			this.localMapValues.put(k, localSubstate);
		} else if (particle instanceof EpochData) {
			var b = serialization.classToByte(particle.getClass());
			var k = SystemMapKey.ofSystem(b);
			this.localMapValues.put(k, localSubstate);
		}
		this.localUpParticles.put(upParticleCount, localSubstate);

		var buf = ByteBuffer.allocate(1024);
		buf.putShort((short) 0);
		serialization.serialize(particle, buf);
		var limit = buf.position();
		buf.putShort(0, (short) (limit - 2));
		buf.position(0);
		buf.limit(limit);
		instruction(REInstruction.REMicroOp.UP, buf);
		upParticleCount++;
		return this;
	}

	public TxLowLevelBuilder localVirtualDown(int index, byte[] virtualKey) {
		if (virtualKey.length > 128) {
			throw new IllegalStateException();
		}
		var buf = ByteBuffer.allocate(Short.BYTES + Short.BYTES + virtualKey.length);
		buf.putShort((short) (virtualKey.length + Short.BYTES));
		buf.putShort((short) index);
		buf.put(virtualKey);
		instruction(REInstruction.REMicroOp.LVDOWN, buf.array());
		return this;
	}

	public TxLowLevelBuilder localVirtualRead(int index, byte[] virtualKey) {
		if (virtualKey.length > 128) {
			throw new IllegalStateException();
		}
		var buf = ByteBuffer.allocate(Short.BYTES + Short.BYTES + virtualKey.length);
		buf.putShort((short) (virtualKey.length + Short.BYTES));
		buf.putShort((short) index);
		buf.put(virtualKey);
		instruction(REInstruction.REMicroOp.LVREAD, buf.array());
		return this;
	}

	public TxLowLevelBuilder virtualDown(SubstateId parent, byte[] virtualKey) {
		if (virtualKey.length > 128) {
			throw new IllegalStateException();
		}
		var id = SubstateId.ofVirtualSubstate(parent, virtualKey);
		var buf = ByteBuffer.allocate(Short.BYTES + id.asBytes().length);
		buf.putShort((short) id.asBytes().length);
		buf.put(id.asBytes());
		instruction(REInstruction.REMicroOp.VDOWN, buf.array());
		return this;
	}

	public TxLowLevelBuilder virtualRead(SubstateId parent, byte[] virtualKey) {
		if (virtualKey.length > 128) {
			throw new IllegalStateException();
		}
		var id = SubstateId.ofVirtualSubstate(parent, virtualKey);
		var buf = ByteBuffer.allocate(Short.BYTES + id.asBytes().length);
		buf.putShort((short) id.asBytes().length);
		buf.put(id.asBytes());
		instruction(REInstruction.REMicroOp.VREAD, buf.array());
		return this;
	}

	public TxLowLevelBuilder localRead(int index) {
		var particle = localUpParticles.get(index);
		if (particle == null) {
			throw new IllegalStateException("Local particle does not exist: " + index);
		}
		instruction(REInstruction.REMicroOp.LREAD, Shorts.toByteArray((short) index));
		return this;
	}

	public TxLowLevelBuilder read(SubstateId substateId) {
		instruction(REInstruction.REMicroOp.READ, substateId.asBytes());
		return this;
	}

	public TxLowLevelBuilder localDown(int index) {
		var particle = localUpParticles.remove(index);
		if (particle == null) {
			throw new IllegalStateException("Local particle does not exist: " + index);
		}
		instruction(REInstruction.REMicroOp.LDOWN, Shorts.toByteArray((short) index));
		return this;
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

	public TxLowLevelBuilder syscall(Syscall syscall, byte[] bytes) throws TxBuilderException {
		if (bytes.length < 1 || bytes.length > 32) {
			throw new TxBuilderException("Length must be >= 1 and <= 32");
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