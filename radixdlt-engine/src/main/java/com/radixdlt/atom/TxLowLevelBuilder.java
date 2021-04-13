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
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.utils.Ints;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Low level builder class for transactions
 */
public final class TxLowLevelBuilder {
	private final List<byte[]> instructions = new ArrayList<>();
	private final Map<Integer, LocalSubstate> localUpParticles = new HashMap<>();
	private final Set<SubstateId> remoteDownSubstate = new HashSet<>();
	private int instructionIndex = 0;

	TxLowLevelBuilder() {
	}

	public static TxLowLevelBuilder newBuilder() {
		return new TxLowLevelBuilder();
	}

	public TxLowLevelBuilder message(String message) {
		var bytes = message.getBytes(StandardCharsets.UTF_8);
		this.instructions.add(new byte[] { REInstruction.REOp.MSG.opCode() });
		this.instructions.add(bytes);
		return this;
	}

	public Set<SubstateId> remoteDownSubstate() {
		return remoteDownSubstate;
	}

	public List<LocalSubstate> localUpSubstate() {
		return new ArrayList<>(localUpParticles.values());
	}

	public TxLowLevelBuilder up(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");
		var particleDson = SubstateSerializer.serialize(particle);
		this.instructions.add(new byte[] { REInstruction.REOp.UP.opCode() });
		this.instructions.add(particleDson);
		this.localUpParticles.put(instructionIndex, LocalSubstate.create(instructionIndex, particle));
		this.instructionIndex++;
		return this;
	}

	public TxLowLevelBuilder virtualDown(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");
		var particleBytes = SubstateSerializer.serialize(particle);
		this.instructions.add(new byte[] { REInstruction.REOp.VDOWN.opCode() });
		this.instructions.add(particleBytes);
		this.remoteDownSubstate.add(SubstateId.ofVirtualSubstate(particleBytes));
		this.instructionIndex++;
		return this;
	}

	public TxLowLevelBuilder localDown(int index) {
		var particle = localUpParticles.remove(index);
		if (particle == null) {
			throw new IllegalStateException("Local particle does not exist: " + index);
		}
		this.instructions.add(new byte[] { REInstruction.REOp.LDOWN.opCode() });
		this.instructions.add(Ints.toByteArray(index));
		this.instructionIndex++;
		return this;
	}

	public TxLowLevelBuilder down(SubstateId substateId) {
		this.instructions.add(new byte[] { REInstruction.REOp.DOWN.opCode() });
		this.instructions.add(substateId.asBytes());
		this.remoteDownSubstate.add(substateId);
		this.instructionIndex++;
		return this;
	}

	public TxLowLevelBuilder read(SubstateId substateId) {
		this.instructions.add(new byte[] { REInstruction.REOp.READ.opCode() });
		this.instructions.add(substateId.asBytes());
		this.instructionIndex++;
		return this;
	}

	public TxLowLevelBuilder localRead(int index) {
		var particle = localUpParticles.get(index);
		if (particle == null) {
			throw new IllegalStateException("Local particle does not exist: " + index);
		}
		this.instructions.add(new byte[] { REInstruction.REOp.LREAD.opCode() });
		this.instructions.add(Ints.toByteArray(index));
		this.instructionIndex++;
		return this;
	}


	public TxLowLevelBuilder particleGroup() {
		this.instructions.add(new byte[] { REInstruction.REOp.END.opCode() });
		this.instructions.add(new byte[0]);
		this.instructionIndex++;
		return this;
	}

	private HashCode computeHashToSign() {
		var outputStream = new ByteArrayOutputStream();
		instructions.forEach(outputStream::writeBytes);
		var firstHash = HashUtils.sha256(outputStream.toByteArray());
		return HashUtils.sha256(firstHash.asBytes());
	}

	private static Txn atomToTxn(Atom atom) {
		var payload = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
		return Txn.create(payload);
	}

	public Atom buildAtomWithoutSignature() {
		return Atom.create(instructions, null);
	}

	public Txn buildWithoutSignature() {
		var atom = Atom.create(instructions, null);
		return atomToTxn(atom);
	}

	public Txn signAndBuild(Function<HashCode, ECDSASignature> signatureProvider) {
		var hashToSign = computeHashToSign();
		var signature = signatureProvider.apply(hashToSign);
		var atom = Atom.create(instructions, signature);
		return atomToTxn(atom);
	}
}