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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.store.SpinStateMachine;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

public final class AtomBuilder {
	private final List<ParticleGroup> particleGroups = new ArrayList<>();
	private final String message;
	private final Map<EUID, ECDSASignature> signatures = new HashMap<>();

	public AtomBuilder() {
		this(ImmutableList.of(), Map.of(), null);
	}

	public AtomBuilder(@Nullable String message) {
		this.message = message;
	}

	public AtomBuilder(ParticleGroup pg) {
		this(List.of(pg), Map.of(), null);
	}

	public AtomBuilder(List<ParticleGroup> pgs) {
		this(pgs, Map.of(), null);
	}

	public AtomBuilder(List<ParticleGroup> particleGroups, Map<EUID, ECDSASignature> signatures, @Nullable String message) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(signatures, "signatures is required");

		this.particleGroups.addAll(particleGroups);
		this.signatures.putAll(signatures);
		this.message = message;
	}

	public AtomBuilder(List<ParticleGroup> particleGroups, String message) {
		this(particleGroups, Map.of(), message);
	}

	public AtomBuilder(List<ParticleGroup> particleGroups, Map<EUID, ECDSASignature> signatures) {
		this(particleGroups, signatures, null);
	}

	// Primarily used for excluding fee groups in fee calculations
	public AtomBuilder copyExcludingGroups(Predicate<ParticleGroup> exclusions) {
		List<ParticleGroup> newParticleGroups = this.particleGroups.stream()
			.filter(pg -> !exclusions.test(pg))
			.collect(Collectors.toList());

		return new AtomBuilder(newParticleGroups, this.signatures, this.message);
	}

	/**
	 * Add a particle group to this atom
	 *
	 * @param particleGroup The particle group
	 */
	public void addParticleGroup(ParticleGroup particleGroup) {
		Objects.requireNonNull(particleGroup, "particleGroup is required");

		this.particleGroups.add(particleGroup);
	}

	/**
	 * Add a singleton particle group to this atom containing the given particle and spin as a SpunParticle
	 *
	 * @param particle The particle
	 * @param spin     The spin
	 */
	public void addParticleGroupWith(Particle particle, Spin spin) {
		this.addParticleGroup(ParticleGroup.of(SpunParticle.of(particle, spin)));
	}

	public String getMessage() {
		return this.message;
	}

	public int getParticleGroupCount() {
		return this.particleGroups.size();
	}

	public Stream<ParticleGroup> particleGroups() {
		return this.particleGroups.stream();
	}

	public List<ParticleGroup> getParticleGroups() {
		return this.particleGroups;
	}

	public Stream<SpunParticle> spunParticles() {
		return this.particleGroups.stream().flatMap(ParticleGroup::spunParticles);
	}

	public Stream<Particle> particles(Spin spin) {
		return this.spunParticles().filter(p -> p.getSpin() == spin).map(SpunParticle::getParticle);
	}

	public <T extends Particle> Stream<T> particles(Class<T> type, Spin spin) {
		return this.particles(type)
			.filter(s -> s.getSpin() == spin)
			.map(SpunParticle::getParticle)
			.map(type::cast);
	}

	public <T extends Particle> Stream<SpunParticle> particles(Class<T> type) {
		return this.spunParticles()
			.filter(p -> type == null || type.isAssignableFrom(p.getParticle().getClass()));
	}

	/**
	 * Returns the first particle found which is assign compatible to the class specified by the type argument.
	 * Returns null if not found
	 *
	 * @param type class of particle to get
	 * @param spin the spin of the particle to get
	 * @return the particle with given type and spin
	 */
	public <T extends Particle> T getParticle(Class<T> type, Spin spin) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(spin);

		return this.spunParticles()
			.filter(s -> s.getSpin().equals(spin))
			.map(SpunParticle::getParticle)
			.filter(p -> type.isAssignableFrom(p.getClass()))
			.map(type::cast)
			.findFirst().orElse(null);
	}


	private static Stream<byte[]> serializedInstructions(ImmutableList<CMMicroInstruction> instructions) {
		final byte[] particleGroupByteCode = new byte[] {0};
		final byte[] checkNeutralThenUpByteCode = new byte[] {1};
		final byte[] checkUpThenDownByteCode = new byte[] {2};

		return instructions.stream().flatMap(i -> {
			if (i.getMicroOp() == CMMicroInstruction.CMMicroOp.PARTICLE_GROUP) {
				return Stream.of(particleGroupByteCode);
			} else {
				final byte[] instByte;
				if (i.getMicroOp() == CMMicroInstruction.CMMicroOp.CHECK_NEUTRAL_THEN_UP) {
					instByte = checkNeutralThenUpByteCode;
				} else if (i.getMicroOp() == CMMicroInstruction.CMMicroOp.CHECK_UP_THEN_DOWN) {
					instByte = checkUpThenDownByteCode;
				} else {
					throw new IllegalStateException();
				}

				byte[] particleDson = DefaultSerialization.getInstance().toDson(i.getParticle(), DsonOutput.Output.ALL);
				return Stream.of(instByte, particleDson);
			}
		});
	}

	static ImmutableList<CMMicroInstruction> toCMMicroInstructions(List<ParticleGroup> particleGroups) {
		final ImmutableList.Builder<CMMicroInstruction> microInstructionsBuilder = new ImmutableList.Builder<>();
		for (int i = 0; i < particleGroups.size(); i++) {
			ParticleGroup pg = particleGroups.get(i);
			for (int j = 0; j < pg.getParticleCount(); j++) {
				SpunParticle sp = pg.getSpunParticle(j);
				Particle particle = sp.getParticle();
				Spin checkSpin = SpinStateMachine.prev(sp.getSpin());
				microInstructionsBuilder.add(CMMicroInstruction.checkSpinAndPush(particle, checkSpin));
			}
			microInstructionsBuilder.add(CMMicroInstruction.particleGroup());
		}

		return microInstructionsBuilder.build();
	}

	public HashCode computeHashToSign() {
		final ImmutableList<CMMicroInstruction> instructions = toCMMicroInstructions(this.getParticleGroups());
		var outputStream = new ByteArrayOutputStream();
		serializedInstructions(instructions).forEach(outputStream::writeBytes);
		var firstHash = HashUtils.sha256(outputStream.toByteArray());
		return HashUtils.sha256(firstHash.asBytes());
	}

	public ClientAtom buildAtom() {
		final ImmutableList<CMMicroInstruction> instructions = toCMMicroInstructions(this.getParticleGroups());
		return new ClientAtom(
			instructions,
			ImmutableMap.copyOf(this.getSignatures()),
			this.getMessage()
		);
	}

	public Map<EUID, ECDSASignature> getSignatures() {
		return signatures;
	}

	public void setSignature(EUID id, ECDSASignature signature) {
		this.signatures.put(id, signature);
	}

	// Property Signatures: 1 getter, 1 setter
	@JsonProperty("signatures")
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private Map<String, ECDSASignature> getJsonSignatures() {
		return this.signatures.entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
	}

	@JsonProperty("signatures")
	private void setJsonSignatures(Map<String, ECDSASignature> sigs) {
		if (sigs != null && !sigs.isEmpty()) {
			this.signatures.putAll((sigs.entrySet().stream()
				.collect(Collectors.toMap(e -> EUID.valueOf(e.getKey()), Map.Entry::getValue))));
		}
	}

	public static AID aidOf(AtomBuilder atom, Hasher hasher) {
		HashCode hash = hasher.hash(atom);
		return AID.from(hash.asBytes());
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