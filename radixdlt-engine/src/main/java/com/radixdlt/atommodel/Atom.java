/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.SerializeWithHid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SerializerId2("radix.atom")
@SerializeWithHid
public final class Atom {

	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	public static final String METADATA_POW_NONCE_KEY = "powNonce";

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private final short version = 100;

	/**
	 * The particle groups and their spin
	 */
	@JsonProperty("particleGroups")
	@DsonOutput(DsonOutput.Output.ALL)
	protected final List<ParticleGroup> particleGroups = new ArrayList<>();

	/**
	 * Contains signers and corresponding signatures of this Atom.
	 */
	protected final Map<EUID, ECDSASignature> signatures = new HashMap<>();

	/**
	 * Metadata about the atom, such as which app made it
	 */
	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	protected final ImmutableMap<String, String> metaData;

	public Atom() {
		this.metaData = ImmutableMap.of();
	}

	public Atom(Map<String, String> metadata) {
		this.metaData = ImmutableMap.<String, String>builder()
			.putAll(metadata)
			.build();
	}

	protected Atom(List<ParticleGroup> particleGroups, Map<EUID, ECDSASignature> signatures) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(signatures, "signatures is required");

		this.particleGroups.addAll(particleGroups);
		this.signatures.putAll(signatures);
		this.metaData = ImmutableMap.of();
	}

	public Atom(List<ParticleGroup> particleGroups, Map<EUID, ECDSASignature> signatures, Map<String, String> metaData) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(signatures, "signatures is required");
		Objects.requireNonNull(metaData, "metaData is required");

		this.particleGroups.addAll(particleGroups);
		this.signatures.putAll(signatures);
		this.metaData = ImmutableMap.copyOf(metaData);
	}

	// copied from legacy Atom.java, temporary hack that will be fixed when ImmutableAtom becomes ImmutableContent
	public Atom copyExcludingMetadata(String... keysToExclude) {
		Objects.requireNonNull(keysToExclude, "keysToRetain is required");

		ImmutableSet<String> keysToExcludeSet = ImmutableSet.copyOf(keysToExclude);
		Map<String, String> filteredMetaData = this.metaData.entrySet().stream()
			.filter(metaDataEntry -> !keysToExcludeSet.contains(metaDataEntry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		return new Atom(this.particleGroups, this.signatures, filteredMetaData);
	}

	// Primarily used for excluding fee groups in fee calculations
	public Atom copyExcludingGroups(Predicate<ParticleGroup> exclusions) {
		List<ParticleGroup> newParticleGroups = this.particleGroups.stream()
			.filter(pg -> !exclusions.test(pg))
			.collect(Collectors.toList());

		return new Atom(newParticleGroups, this.signatures, this.metaData);
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


	/**
	 * Add two particle groups to this atom
	 *
	 * @param particle0 The first particle
	 * @param spin0     The spin of first particle
	 * @param particle1 The second particle
	 * @param spin1     The spin of second particle
	 */
	public void addParticleGroupWith(Particle particle0, Spin spin0, Particle particle1, Spin spin1) {
		this.addParticleGroup(ParticleGroup.of(SpunParticle.of(particle0, spin0), SpunParticle.of(particle1, spin1)));
	}


	/**
	 * Add three particle groups to this atom
	 *
	 * @param particle0 The first particle
	 * @param spin0     The spin of first particle
	 * @param particle1 The second particle
	 * @param spin1     The spin of second particle
	 * @param particle2 The third particle
	 * @param spin2     The spin of third particle
	 */
	public void addParticleGroupWith(Particle particle0, Spin spin0, Particle particle1, Spin spin1, Particle particle2, Spin spin2) {
		this.addParticleGroup(
			ParticleGroup.of(
				SpunParticle.of(particle0, spin0),
				SpunParticle.of(particle1, spin1),
				SpunParticle.of(particle2, spin2)
			)
		);
	}

	// SIGNATURES //
	public boolean verify(Collection<ECPublicKey> keys, Hasher hasher) throws PublicKeyException {
		return verify(keys, keys.size(), hasher);
	}

	public boolean verify(Collection<ECPublicKey> keys, int requirement, Hasher hasher) throws PublicKeyException {
		if (getSignatures().isEmpty()) {
			throw new PublicKeyException("No signatures set, can not verify");
		}

		int verified = 0;
		HashCode hash = hasher.hash(this);
		byte[] hashBytes = hash.asBytes();
		ECDSASignature signature = null;

		for (ECPublicKey key : keys) {
			signature = getSignatures().get(key.euid());

			if (signature == null) {
				continue;
			}

			if (key.verify(hashBytes, signature)) {
				verified++;
			}
		}

		return verified >= requirement;
	}

	public boolean verify(ECPublicKey key, Hasher hasher) throws PublicKeyException {
		if (getSignatures().isEmpty()) {
			throw new PublicKeyException("No signatures set, can not verify");
		}

		HashCode hash = hasher.hash(this);

		ECDSASignature signature = getSignatures().get(key.euid());

		if (signature == null) {
			return false;
		}

		return key.verify(hash, signature);
	}

	public Map<EUID, ECDSASignature> getSignatures() {
		return Collections.unmodifiableMap(this.signatures);
	}

	public ECDSASignature getSignature(EUID id) {
		return this.signatures.get(id);
	}

	private void setSignature(EUID id, ECDSASignature signature) {
		this.signatures.put(id, signature);
	}

	public void sign(ECKeyPair key, Hasher hasher) throws AtomAlreadySignedException {
		if (!getSignatures().isEmpty()) {
			throw new AtomAlreadySignedException("Atom already signed, cannot sign again.");
		}

		HashCode hash = hasher.hash(this);
		setSignature(key.euid(), key.sign(hash.asBytes()));
	}

	public void sign(Collection<ECKeyPair> keys, Hasher hasher) throws AtomAlreadySignedException {
		if (!getSignatures().isEmpty()) {
			throw new AtomAlreadySignedException("Atom already signed, cannot sign again.");
		}

		HashCode hash = hasher.hash(this);

		for (ECKeyPair key : keys) {
			setSignature(key.euid(), key.sign(hash.asBytes()));
		}
	}

	/**
	 * Returns the index of a given ParticleGroup
	 * Returns -1 if not found
	 *
	 * @param particleGroup the particle group to look for
	 * @return index of the particle group
	 */
	public int indexOfParticleGroup(ParticleGroup particleGroup) {
		return this.particleGroups.indexOf(particleGroup);
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

	public ParticleGroup getParticleGroup(int particleGroupIndex) {
		return this.particleGroups.get(particleGroupIndex);
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

	/**
	 * Get the metadata associated with the atom
	 *
	 * @return an immutable map of the metadata
	 */
	public Map<String, String> getMetaData() {
		return this.metaData;
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

	public static AID aidOf(Atom atom, Hasher hasher) {
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
		if (!(o instanceof Atom)) {
			return false;
		}
		Atom atom = (Atom) o;
		return version == atom.version
				&& Objects.equals(particleGroups, atom.particleGroups)
				&& Objects.equals(signatures, atom.signatures)
				&& Objects.equals(metaData, atom.metaData);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, particleGroups, signatures, metaData);
	}
}