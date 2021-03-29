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
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.utils.Ints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Low level builder class for transactions
 */
public final class TxLowLevelBuilder {
	private String message;
	private final List<REInstruction> instructions = new ArrayList<>();
	private final Map<Integer, LocalSubstate> localUpParticles = new HashMap<>();

	TxLowLevelBuilder() {
	}

	public static TxLowLevelBuilder newBuilder() {
		return new TxLowLevelBuilder();
	}

	public TxLowLevelBuilder message(String message) {
		this.message = message;
		return this;
	}

	public List<LocalSubstate> localUpSubstate() {
		return new ArrayList<>(localUpParticles.values());
	}

	public TxLowLevelBuilder up(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");
		var particleDson = DefaultSerialization.getInstance().toDson(particle, DsonOutput.Output.ALL);
		var nextIndex = this.instructions.size();
		this.instructions.add(
			REInstruction.create(
				REInstruction.REOp.UP.opCode(),
				particleDson
			)
		);
		localUpParticles.put(nextIndex, new LocalSubstate(nextIndex, particle));
		return this;
	}

	public TxLowLevelBuilder virtualDown(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");
		var particleDson = DefaultSerialization.getInstance().toDson(particle, DsonOutput.Output.ALL);
		this.instructions.add(
			REInstruction.create(
				REInstruction.REOp.VDOWN.opCode(),
				particleDson
			)
		);
		return this;
	}

	public TxLowLevelBuilder localDown(int index) {
		var particle = localUpParticles.remove(index);
		if (particle == null) {
			throw new IllegalStateException("Local particle does not exist: " + index);
		}
		this.instructions.add(REInstruction.create(REInstruction.REOp.LDOWN.opCode(), Ints.toByteArray(index)));

		return this;
	}

	public TxLowLevelBuilder down(SubstateId substateId) {
		this.instructions.add(REInstruction.create(REInstruction.REOp.DOWN.opCode(), substateId.asBytes()));
		localUpParticles.remove(substateId);
		return this;
	}

	public TxLowLevelBuilder particleGroup() {
		this.instructions.add(REInstruction.particleGroup());
		return this;
	}

	private HashCode computeHashToSign() {
		return Atom.computeHashToSign(instructions);
	}

	public Atom buildWithoutSignature() {
		return Atom.create(
			instructions,
			null,
			this.message
		);
	}

	public Atom signAndBuild(Function<HashCode, ECDSASignature> signatureProvider) {
		var hashToSign = computeHashToSign();
		var signature = signatureProvider.apply(hashToSign);
		return Atom.create(
			instructions,
			signature,
			this.message
		);
	}
}