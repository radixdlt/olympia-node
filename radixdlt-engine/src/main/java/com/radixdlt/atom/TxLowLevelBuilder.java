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

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECDSASignature;

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
	private final ImmutableList.Builder<CMMicroInstruction> instructions = ImmutableList.builder();
	private final Map<ParticleId, Particle> localUpParticles = new HashMap<>();

	TxLowLevelBuilder() {
	}

	public TxLowLevelBuilder message(String message) {
		this.message = message;
		return this;
	}

	public List<Particle> localUpParticles() {
		return new ArrayList<>(localUpParticles.values());
	}

	public TxLowLevelBuilder spinUp(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");
		this.instructions.add(CMMicroInstruction.spinUp(particle));
		localUpParticles.put(ParticleId.of(particle), particle);
		return this;
	}

	public TxLowLevelBuilder virtualSpinDown(Particle particle) {
		Objects.requireNonNull(particle, "particle is required");
		this.instructions.add(CMMicroInstruction.virtualSpinDown(particle));
		return this;
	}

	public TxLowLevelBuilder spinDown(ParticleId particleId) {
		this.instructions.add(CMMicroInstruction.spinDown(particleId));
		localUpParticles.remove(particleId);
		return this;
	}

	public TxLowLevelBuilder particleGroup() {
		this.instructions.add(CMMicroInstruction.particleGroup());
		return this;
	}

	private HashCode computeHashToSign() {
		return Atom.computeHashToSign(instructions.build());
	}

	public Atom buildWithoutSignature() {
		return Atom.create(
			instructions.build(),
			null,
			this.message
		);
	}

	public Atom signAndBuild(Function<HashCode, ECDSASignature> signatureProvider) {
		var hashToSign = computeHashToSign();
		var signature = signatureProvider.apply(hashToSign);
		return Atom.create(
			instructions.build(),
			signature,
			this.message
		);
	}
}