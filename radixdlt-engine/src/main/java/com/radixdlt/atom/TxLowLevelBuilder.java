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
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.utils.Ints;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Low level builder class for transactions
 */
public final class TxLowLevelBuilder {
	private final ByteArrayOutputStream blobStream;
	private final Map<Integer, LocalSubstate> localUpParticles = new HashMap<>();
	private final Set<SubstateId> remoteDownSubstate = new HashSet<>();
	private int instructionIndex = 0;

	TxLowLevelBuilder(ByteArrayOutputStream blobStream) {
		this.blobStream = blobStream;
	}

	public static TxLowLevelBuilder newBuilder(byte[] blob) {
		var blobStream = new ByteArrayOutputStream();
		try {
			blobStream.write(blob);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write data.");
		}
		return new TxLowLevelBuilder(blobStream);
	}

	public static TxLowLevelBuilder newBuilder() {
		return new TxLowLevelBuilder(new ByteArrayOutputStream());
	}

	public Set<SubstateId> remoteDownSubstate() {
		return remoteDownSubstate;
	}

	public List<LocalSubstate> localUpSubstate() {
		return new ArrayList<>(localUpParticles.values());
	}

	// TODO: Remove array copies
	private byte[] varLengthData(byte[] bytes) {
		if (bytes.length > 255) {
			throw new IllegalArgumentException();
		}
		var data = new byte[1 + bytes.length];
		System.arraycopy(bytes, 0, data, 1, bytes.length);
		data[0] = (byte) bytes.length;
		return data;
	}

	private void instruction(REInstruction.REOp op, byte[] data) {
		blobStream.write(op.opCode());
		try {
			blobStream.write(data);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to write data.");
		}
		this.instructionIndex++;
	}

	public TxLowLevelBuilder message(String message) {
		var bytes = message.getBytes(StandardCharsets.UTF_8);
		instruction(REInstruction.REOp.MSG, varLengthData(bytes));
		return this;
	}

	public TxLowLevelBuilder up(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");
		this.localUpParticles.put(instructionIndex, LocalSubstate.create(instructionIndex, particle));
		var bytes = RESerializer.serialize(particle);
		instruction(REInstruction.REOp.UP, bytes);
		return this;
	}

	public TxLowLevelBuilder virtualDown(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");
		var bytes = RESerializer.serialize(particle);
		instruction(REInstruction.REOp.VDOWN, bytes);
		this.remoteDownSubstate.add(SubstateId.ofVirtualSubstate(bytes));
		return this;
	}

	public TxLowLevelBuilder localDown(int index) {
		var particle = localUpParticles.remove(index);
		if (particle == null) {
			throw new IllegalStateException("Local particle does not exist: " + index);
		}
		instruction(REInstruction.REOp.LDOWN, Ints.toByteArray(index));
		return this;
	}

	public TxLowLevelBuilder down(SubstateId substateId) {
		instruction(REInstruction.REOp.DOWN, substateId.asBytes());
		this.remoteDownSubstate.add(substateId);
		return this;
	}

	public TxLowLevelBuilder read(SubstateId substateId) {
		instruction(REInstruction.REOp.READ, substateId.asBytes());
		return this;
	}

	public TxLowLevelBuilder localRead(int index) {
		var particle = localUpParticles.get(index);
		if (particle == null) {
			throw new IllegalStateException("Local particle does not exist: " + index);
		}
		instruction(REInstruction.REOp.LREAD, Ints.toByteArray(index));
		return this;
	}

	public TxLowLevelBuilder particleGroup() {
		instruction(REInstruction.REOp.END, new byte[0]);
		return this;
	}

	public byte[] blob() {
		return blobStream.toByteArray();
	}

	public HashCode hashToSign() {
		var firstHash = HashUtils.sha256(blob());
		return HashUtils.sha256(firstHash.asBytes());
	}

	public TxLowLevelBuilder sig(ECDSASignature signature) {
		instruction(REInstruction.REOp.SIG, RESerializer.serializeSignature(signature));
		return this;
	}

	public Txn build() {
		return Txn.create(blobStream.toByteArray());
	}
}