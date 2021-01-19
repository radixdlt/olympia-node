/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import com.radixdlt.identifiers.RadixAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An atom is the fundamental atomic unit of storage on the ledger (similar to a block
 * in a blockchain) and defines the actions that can be issued onto the ledger.
 */
@SerializerId2("radix.atom")
public final class Atom {
	public static final String METADATA_TIMESTAMP_KEY = "timestamp";
	public static final String METADATA_POW_NONCE_KEY = "powNonce";

	public static Atom create(ParticleGroup particleGroup) {
		return new Atom(
			ImmutableList.of(particleGroup),
			ImmutableMap.of(),
			ImmutableMap.of()
		);
	}

	public static Atom create(List<ParticleGroup> particleGroups) {
		return new Atom(
			ImmutableList.copyOf(particleGroups),
			ImmutableMap.of(),
			ImmutableMap.of()
		);
	}

	public static Atom create(List<ParticleGroup> particleGroups, Map<String, String> metaData) {
		return new Atom(
			ImmutableList.copyOf(particleGroups),
			ImmutableMap.copyOf(metaData),
			ImmutableMap.of()
		);
	}

	@JsonProperty("particleGroups")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableList<ParticleGroup> particleGroups;

	@JsonProperty("signatures")
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private final ImmutableMap<String, ECDSASignature> signatures;

	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableMap<String, String> metaData;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	// TODO serializer id for atoms is temporarily excluded from hash for compatibility with abstract atom
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	private Atom() {
		this.metaData = null;
		this.signatures = null;
		this.particleGroups = null;
	}

	private Atom(
		ImmutableList<ParticleGroup> particleGroups,
		ImmutableMap<String, String> metaData,
		ImmutableMap<String, ECDSASignature> signatures
	) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(metaData, "metaData is required");
		Objects.requireNonNull(signatures, "signatures are required");

		this.particleGroups = particleGroups;
		this.metaData = metaData;
		this.signatures = signatures;
	}

	// TODO: refactor to utilize an AtomBuilder
	public Atom addSignature(EUID signatureId, ECDSASignature signature) {
		ImmutableMap.Builder<String, ECDSASignature> builder = ImmutableMap.builder();
		signatures.forEach((id, sig) -> {
			if (!id.equals(signatureId.toString())) {
				builder.put(id, sig);
			}
		});
		builder.put(signatureId.toString(), signature);

		return new Atom(
			this.particleGroups,
			this.metaData,
			builder.build()
		);
	}


	public Stream<ParticleGroup> particleGroups() {
		return this.particleGroups.stream();
	}

	public Stream<SpunParticle> spunParticles() {
		return this.particleGroups.stream().flatMap(ParticleGroup::spunParticles);
	}

	public Stream<Particle> particles(Spin spin) {
		return this.spunParticles().filter(s -> s.getSpin() == spin).map(SpunParticle::getParticle);
	}

	public Stream<RadixAddress> addresses() {
		return this.spunParticles()
			.map(SpunParticle::getParticle)
			.map(Particle::getShardables)
			.flatMap(Set::stream)
			.distinct();
	}

	public Map<String, ECDSASignature> getSignatures() {
		return this.signatures;
	}

	public Optional<ECDSASignature> getSignature(EUID uid) {
		return Optional.ofNullable(this.signatures).map(sigs -> sigs.get(uid.toString()));
	}

	public byte[] toDson() {
		return Serialize.getInstance().toDson(this, DsonOutput.Output.HASH);
	}

	public HashCode getHash() {
		return HashUtils.sha256(toDson());
	}

	public AID getAid() {
		return AID.from(getHash().asBytes());
	}

	/**
	 * Get the metadata associated with the atom
	 *
	 * @return an immutable map of the meta data
	 */
	public Map<String, String> getMetaData() {
		return this.metaData;
	}

	@Override
	public int hashCode() {
		return this.getHash().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Atom)) {
			return false;
		}

		Atom atom = (Atom) o;
		return this.getHash().equals(atom.getHash());
	}

	@Override
	public String toString() {
		String particleGroupsStr = this.particleGroups.stream().map(ParticleGroup::toString).collect(Collectors.joining(","));
		return String.format("%s[%s:%s]", getClass().getSimpleName(), getAid(), particleGroupsStr);
	}
}
