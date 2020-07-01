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
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SerializerId2("radix.atom")
public class Atom {

	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	public static final String METADATA_TIMESTAMP_KEY = "timestamp";
	public static final String METADATA_POW_NONCE_KEY = "powNonce";

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

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

	private final Supplier<AID> cachedAID = Suppliers.memoize(this::doGetAID);
	private final Supplier<Hash> cachedHash = Suppliers.memoize(this::doGetHash);

	public Atom() {
		this.metaData = ImmutableMap.of();
	}

	public Atom(long timestamp) {
		this.metaData = ImmutableMap.of(METADATA_TIMESTAMP_KEY, String.valueOf(timestamp));
	}

	public Atom(long timestamp, Map<String, String> metadata) {
		this.metaData = ImmutableMap.<String, String>builder()
			.put(METADATA_TIMESTAMP_KEY, String.valueOf(timestamp))
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
	public boolean verify(Collection<ECPublicKey> keys) throws CryptoException {
		return this.verify(keys, keys.size());
	}

	public boolean verify(Collection<ECPublicKey> keys, int requirement) throws CryptoException {
		if (this.signatures.isEmpty()) {
			throw new CryptoException("No signatures set, can not verify");
		}

		int verified = 0;
		Hash hash = this.getHash();
		byte[] hashBytes = hash.toByteArray();
		ECDSASignature signature = null;

		for (ECPublicKey key : keys) {
			signature = this.signatures.get(key.euid());

			if (signature == null) {
				continue;
			}

			if (key.verify(hashBytes, signature)) {
				verified++;
			}
		}

		return verified >= requirement;
	}

	public boolean verify(ECPublicKey key) throws CryptoException {
		if (this.signatures.isEmpty()) {
			throw new CryptoException("No signatures set, can not verify");
		}

		Hash hash = this.getHash();

		ECDSASignature signature = this.signatures.get(key.euid());

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

	public void sign(ECKeyPair key) throws CryptoException {
		if (!this.signatures.isEmpty()) {
			throw new AtomAlreadySignedException("Atom already signed, cannot sign again.");
		}

		Hash hash = this.getHash();
		this.setSignature(key.euid(), key.sign(hash.toByteArray()));
	}

	public void sign(Collection<ECKeyPair> keys) throws CryptoException {
		if (!this.signatures.isEmpty()) {
			throw new AtomAlreadySignedException("Atom already signed, cannot sign again.");
		}

		Hash hash = this.getHash();

		for (ECKeyPair key : keys) {
			this.setSignature(key.euid(), key.sign(hash.toByteArray()));
		}
	}


	/**
	 * Gets the memoized AID of this Atom.
	 * Note that once called, the result of this operation is cached.
	 * This is a temporary interface and will be removed later
	 * as it introduces a dependency to the CM in Atom.
	 */
	public final AID getAID() {
		return cachedAID.get();
	}

	private AID doGetAID() {
		return AID.from(getHash().toByteArray());
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

	public final Stream<ParticleGroup> particleGroups() {
		return this.particleGroups.stream();
	}

	public final List<ParticleGroup> getParticleGroups() {
		return this.particleGroups;
	}

	public final ParticleGroup getParticleGroup(int particleGroupIndex) {
		return this.particleGroups.get(particleGroupIndex);
	}

	public final Stream<SpunParticle> spunParticles() {
		return this.particleGroups.stream().flatMap(ParticleGroup::spunParticles);
	}

	public final Stream<Particle> particles(Spin spin) {
		return this.spunParticles().filter(p -> p.getSpin() == spin).map(SpunParticle::getParticle);
	}

	public final <T extends Particle> Stream<T> particles(Class<T> type, Spin spin) {
		return this.particles(type)
			.filter(s -> s.getSpin() == spin)
			.map(SpunParticle::getParticle)
			.map(type::cast);
	}

	public final <T extends Particle> Stream<SpunParticle> particles(Class<T> type) {
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
	public final <T extends Particle> T getParticle(Class<T> type, Spin spin) {
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

	private Hash doGetHash() {
		try {
			return Hash.of(DefaultSerialization.getInstance().toDson(this, DsonOutput.Output.HASH));
		} catch (Exception e) {
			throw new IllegalStateException("Error generating hash: " + e, e);
		}
	}

	public Hash getHash() {
		return cachedHash.get();
	}

	@JsonProperty("hid")
	@DsonOutput(DsonOutput.Output.API)
	public final EUID euid() {
		return getHash().euid();
	}


	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		if (o == this) {
			return true;
		}

		return getClass().isInstance(o) && getHash().equals(((Atom) o).getHash());
	}

	@Override
	public int hashCode() {
		return getHash().hashCode();
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

	public boolean hasTimestamp() {
		return this.metaData.containsKey(METADATA_TIMESTAMP_KEY);
	}

	@Override
	public String toString() {
		String particleGroupsStr = this.particleGroups.stream().map(ParticleGroup::toString).collect(Collectors.joining(","));
		return String.format("%s[%s:%s]", getClass().getSimpleName(), getAID(), particleGroupsStr);
	}
}