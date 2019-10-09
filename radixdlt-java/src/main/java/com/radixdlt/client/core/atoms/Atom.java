package com.radixdlt.client.core.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECSignature;
import org.radix.common.ID.AID;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerConstants;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;

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

	public static Atom create(ParticleGroup particleGroup, long timestamp) {
		return new Atom(
			ImmutableList.of(particleGroup),
			ImmutableMap.of(METADATA_TIMESTAMP_KEY, String.valueOf(timestamp)),
			ImmutableMap.of()
		);
	}

	public static Atom create(List<ParticleGroup> particleGroups, long timestamp) {
		return new Atom(
			ImmutableList.copyOf(particleGroups),
			ImmutableMap.of(METADATA_TIMESTAMP_KEY, String.valueOf(timestamp)),
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
	private final ImmutableMap<String, ECSignature> signatures;

	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableMap<String, String> metaData;

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

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
		ImmutableMap<String, ECSignature> signatures
	) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(metaData, "metaData is required");
		Objects.requireNonNull(signatures, "signatures are required");

		this.particleGroups = particleGroups;
		this.metaData = metaData;
		this.signatures = signatures;
	}

	// TODO: refactor to utilize an AtomBuilder
	public Atom addSignature(EUID signatureId, ECSignature signature) {
		return new Atom(
			this.particleGroups,
			this.metaData,
			ImmutableMap.<String, ECSignature>builder()
				.putAll(this.signatures)
				.put(signatureId.toString(), signature)
				.build()
		);
	}

	private Set<Long> getShards() {
		return this.spunParticles()
			.map(SpunParticle<Particle>::getParticle)
			.map(Particle::getDestinations)
			.flatMap(Set::stream)
			.map(EUID::getShard)
			.collect(Collectors.toSet());
	}

	// HACK
	public Set<Long> getRequiredFirstShard() {
		if (this.spunParticles().anyMatch(s -> s.getSpin() == Spin.DOWN)) {
			return this.spunParticles()
				.filter(s -> s.getSpin() == Spin.DOWN)
				.flatMap(s -> s.getParticle().getShardables().stream())
				.map(RadixAddress::getUID)
				.map(EUID::getShard)
				.collect(Collectors.toSet());
		} else {
			return this.getShards();
		}
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
			.map(SpunParticle<Particle>::getParticle)
			.map(Particle::getShardables)
			.flatMap(Set::stream)
			.distinct();
	}

	public boolean hasTimestamp() {
		return this.metaData.containsKey(METADATA_TIMESTAMP_KEY);
	}

	/**
	 * Convenience method to retrieve timestamp
	 *
	 * @return The timestamp in milliseconds since epoch
	 */
	public long getTimestamp() {
		// TODO Not happy with this error handling as it moves some validation work into the atom data. See RLAU-951
		try {
			return Long.parseLong(this.metaData.get(METADATA_TIMESTAMP_KEY));
		} catch (NumberFormatException e) {
			return Long.MIN_VALUE;
		}
	}

	public Map<String, ECSignature> getSignatures() {
		return this.signatures;
	}

	public Optional<ECSignature> getSignature(EUID uid) {
		return Optional.ofNullable(this.signatures).map(sigs -> sigs.get(uid.toString()));
	}

	public byte[] toDson() {
		return Serialize.getInstance().toDson(this, DsonOutput.Output.HASH);
	}

	public RadixHash getHash() {
		return RadixHash.of(toDson());
	}

	public AID getAid() {
		return AID.from(getHash(), getShards());
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
