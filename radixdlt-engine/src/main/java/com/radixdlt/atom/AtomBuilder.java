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
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builder class for atom creation
 */
public final class AtomBuilder {
	private final List<ParticleGroup> particleGroups = new ArrayList<>();
	private String message;
	private final Map<EUID, ECDSASignature> signatures = new HashMap<>();
	private final Map<HashCode, Particle> localUpParticles = new HashMap<>();

	AtomBuilder() {
	}

	public AtomBuilder message(String message) {
		this.message = message;
		return this;
	}

	public Stream<Particle> localUpParticles() {
		return localUpParticles.values().stream();
	}

	/**
	 * Add a particle group to this atom
	 *
	 * @param particleGroup The particle group
	 */
	public AtomBuilder addParticleGroup(ParticleGroup particleGroup) {
		Objects.requireNonNull(particleGroup, "particleGroup is required");
		this.particleGroups.add(particleGroup);
		particleGroup.getInstructions().forEach(i -> {
			if (i.getMicroOp() == CMMicroInstruction.CMMicroOp.SPIN_DOWN) {
				localUpParticles.remove(i.getParticleHash());
			} else if (i.getMicroOp() == CMMicroInstruction.CMMicroOp.SPIN_UP) {
				var dson = DefaultSerialization.getInstance().toDson(i.getParticle(), DsonOutput.Output.ALL);
				var particleHash = HashUtils.sha256(dson);
				localUpParticles.put(particleHash, i.getParticle());
			}
		});

		return this;
	}

	static ImmutableList<CMMicroInstruction> toCMMicroInstructions(List<ParticleGroup> particleGroups) {
		final ImmutableList.Builder<CMMicroInstruction> microInstructionsBuilder = new ImmutableList.Builder<>();
		for (int i = 0; i < particleGroups.size(); i++) {
			ParticleGroup pg = particleGroups.get(i);
			microInstructionsBuilder.addAll(pg.getInstructions());
			microInstructionsBuilder.add(CMMicroInstruction.particleGroup());
		}
		return microInstructionsBuilder.build();
	}

	public HashCode computeHashToSign() {
		return Atom.computeHashToSign(toCMMicroInstructions(this.particleGroups));
	}

	public Atom buildAtom() {
		final var instructions = toCMMicroInstructions(this.particleGroups);
		return Atom.create(
			instructions,
			ImmutableMap.copyOf(this.signatures),
			this.message
		);
	}

	public void setSignature(EUID id, ECDSASignature signature) {
		this.signatures.put(id, signature);
	}

	@Override
	public String toString() {
		String particleGroupsStr = this.particleGroups.stream().map(ParticleGroup::toString).collect(Collectors.joining(","));
		return String.format("%s[%s]", getClass().getSimpleName(), particleGroupsStr);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AtomBuilder)) {
			return false;
		}
		AtomBuilder atom = (AtomBuilder) o;
		return Objects.equals(particleGroups, atom.particleGroups)
				&& Objects.equals(signatures, atom.signatures)
				&& Objects.equals(message, atom.message);
	}

	@Override
	public int hashCode() {
		return Objects.hash(particleGroups, signatures, message);
	}
}