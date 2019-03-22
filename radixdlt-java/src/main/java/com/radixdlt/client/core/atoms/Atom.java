package com.radixdlt.client.core.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.tokens.ConsumableTokens;
import com.radixdlt.client.atommodel.tokens.ConsumingTokens;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import org.radix.common.ID.EUID;
import org.radix.common.tuples.Pair;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;
import org.radix.serialization2.client.Serialize;
import org.radix.utils.UInt256s;

import java.math.BigInteger;
import java.util.ArrayList;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Collections;
import java.util.HashMap;
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
@SerializerId2("ATOM")
public final class Atom extends SerializableObject {
	public static final String METADATA_TIMESTAMP_KEY = "timestamp";

	@JsonProperty("particleGroups")
	@DsonOutput(DsonOutput.Output.ALL)
	private final List<ParticleGroup> particleGroups = new ArrayList<>();

	@JsonProperty("signatures")
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private final Map<String, ECSignature> signatures = new HashMap<>();

	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableMap<String, String> metaData;

	private Atom() {
		this(0);
	}

	public Atom(long timestamp) {
		this(Collections.emptyList(), timestamp);
	}

	public Atom(List<ParticleGroup> particleGroups, long timestamp) {
		this(particleGroups, ImmutableMap.of(METADATA_TIMESTAMP_KEY, String.valueOf(timestamp)));
	}

	public Atom(List<ParticleGroup> particleGroups, Map<String, String> metaData) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(metaData, "metaData is required");

		this.particleGroups.addAll(particleGroups);
		this.metaData = ImmutableMap.copyOf(metaData);
	}

	private Atom(
		List<ParticleGroup> particleGroups,
		Map<String, String> metaData,
		EUID signatureId,
		ECSignature signature
	) {
		this(particleGroups, metaData);

		Objects.requireNonNull(signatureId, "signatureId is required");
		Objects.requireNonNull(signature, "signature is required");

		this.signatures.put(signatureId.toString(), signature);
	}

	public Atom withSignature(ECSignature signature, EUID signatureId) {
		return new Atom(
			this.particleGroups,
			this.metaData,
			signatureId,
			signature
		);
	}

	private Set<Long> getShards() {
		return this.spunParticles()
			.map(SpunParticle<Particle>::getParticle)
			.map(Particle::getShardables)
			.flatMap(Set::stream)
			.map(RadixAddress::getUID)
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
			.flatMap(Set::stream);
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

	public Stream<Pair<ConsumableTokens, Spin>> consumableTokens() {
		return this.spunParticles()
			.filter(s -> s.getParticle() instanceof ConsumableTokens)
			.map(s -> Pair.of((ConsumableTokens) s.getParticle(), s.getSpin()));
	}

	public Stream<Pair<ConsumingTokens, Spin>> consumingTokens() {
		return this.spunParticles()
			.filter(s -> s.getParticle() instanceof ConsumingTokens)
			.map(s -> Pair.of((ConsumingTokens) s.getParticle(), s.getSpin()));
	}

	public List<ConsumableTokens> getConsumableParticles(Spin spin) {
		return this.spunParticles()
			.filter(s -> s.getSpin() == spin)
			.map(SpunParticle::getParticle)
			.filter(p -> p instanceof ConsumableTokens)
			.map(p -> (ConsumableTokens) p)
			.collect(Collectors.toList());
	}

	public byte[] toDson() {
		return Serialize.getInstance().toDson(this, DsonOutput.Output.HASH);
	}

	public RadixHash getHash() {
		return RadixHash.of(toDson());
	}

	public EUID getHid() {
		return this.getHash().toEUID();
	}

	public List<MessageParticle> getMessageParticles() {
		return this.spunParticles()
			.map(SpunParticle::getParticle)
			.filter(p -> p instanceof MessageParticle)
			.map(p -> (MessageParticle) p)
			.collect(Collectors.toList());
	}

	public Map<TokenDefinitionReference, Map<RadixAddress, BigInteger>> tokenSummary() {
		return this.consumableTokens()
			.collect(Collectors.groupingBy(
				tokens -> tokens.getFirst().getTokenDefinitionReference(),
				Collectors.groupingBy(
					tokens -> tokens.getFirst().getAddress(),
					Collectors.reducing(BigInteger.ZERO, this::consumableToAmount, BigInteger::add)
				)
			));
	}

	private BigInteger consumableToAmount(Pair<ConsumableTokens, Spin> tokens) {
		BigInteger amount = UInt256s.toBigInteger(tokens.getFirst().getAmount());
		return tokens.getSecond() == Spin.DOWN ? amount.negate() : amount;
	}
	/**
	 * Get the metadata associated with the atom
	 * @return an immutable map of the meta data
	 */
	public Map<String, String> getMetaData() {
		return this.metaData;
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
	public int hashCode() {
		return this.getHash().hashCode();
	}

	@Override
	public String toString() {
		String particleGroupsStr = this.particleGroups.stream().map(ParticleGroup::toString).collect(Collectors.joining(","));
		return String.format("%s[%s:%s]", getClass().getSimpleName(), getHid(), particleGroupsStr);
	}
}
