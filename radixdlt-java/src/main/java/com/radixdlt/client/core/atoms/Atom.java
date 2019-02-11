package com.radixdlt.client.core.atoms;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;
import org.radix.serialization2.client.Serialize;
import org.radix.utils.UInt256s;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.application.translate.tokens.TokenClassReference;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.timestamp.TimestampParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;

/**
 * An atom is the fundamental atomic unit of storage on the ledger (similar to a block
 * in a blockchain) and defines the actions that can be issued onto the ledger.
 */
@SerializerId2("ATOM")
public final class Atom extends SerializableObject {
	@JsonProperty("particleGroups")
	@DsonOutput(DsonOutput.Output.ALL)
	private final List<ParticleGroup> particleGroups = new ArrayList<>();

	@JsonProperty("signatures")
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private final Map<String, ECSignature> signatures = new HashMap<>();

	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, String> metaData = new HashMap<>();

	private Atom() {
	}

	public Atom(List<ParticleGroup> particleGroups) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");

		this.particleGroups.addAll(particleGroups);
	}

	public Atom(List<ParticleGroup> particleGroups, Map<String, String> metaData) {
		Objects.requireNonNull(particleGroups, "particleGroups is required");
		Objects.requireNonNull(metaData, "particleGroups is required");

		this.particleGroups.addAll(particleGroups);
		this.metaData.putAll(metaData);
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
			.map(Particle::getKeyDestinations)
			.flatMap(Set::stream)
			.map(ECPublicKey::getUID)
			.map(EUID::getShard)
			.collect(Collectors.toSet());
	}

	// HACK
	public Set<Long> getRequiredFirstShard() {
		if (this.spunParticles().anyMatch(s -> s.getSpin() == Spin.DOWN)) {
			return this.spunParticles()
				.filter(s -> s.getSpin() == Spin.DOWN)
				.flatMap(s -> s.getParticle().getKeyDestinations().stream())
				.map(ECPublicKey::getUID)
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

	public Stream<ECPublicKey> addresses() {
		return this.spunParticles()
			.map(SpunParticle<Particle>::getParticle)
			.map(Particle::getKeyDestinations)
			.flatMap(Set::stream);
	}

	public Long getTimestamp() {
		return this.spunParticles()
			.map(SpunParticle::getParticle)
			.filter(p -> p instanceof TimestampParticle)
			.map(p -> ((TimestampParticle) p).getTimestamp()).findAny()
			.orElse(0L);
	}

	public Map<String, ECSignature> getSignatures() {
		return this.signatures;
	}

	public Optional<ECSignature> getSignature(EUID uid) {
		return Optional.ofNullable(this.signatures).map(sigs -> sigs.get(uid.toString()));
	}

	public Stream<SpunParticle<OwnedTokensParticle>> ownedTokensParticles() {
		return this.spunParticles()
			.filter(s -> s.getParticle() instanceof OwnedTokensParticle)
			.map(s -> (SpunParticle<OwnedTokensParticle>) s);
}

	public List<OwnedTokensParticle> getOwnedTokensParticles(Spin spin) {
		return this.spunParticles()
			.filter(s -> s.getSpin() == spin)
			.map(SpunParticle::getParticle)
			.filter(p -> p instanceof OwnedTokensParticle)
			.map(p -> (OwnedTokensParticle) p)
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

	public Map<TokenClassReference, Map<ECPublicKey, BigInteger>> tokenSummary() {
		return this.ownedTokensParticles()
			.collect(Collectors.groupingBy(
				s -> s.getParticle().getTokenClassReference(),
				Collectors.groupingBy(
					s -> s.getParticle().getOwner(),
					Collectors.reducing(BigInteger.ZERO, Atom::ownedTokensToBigInteger, BigInteger::add)
				)
			));
	}

	private static BigInteger ownedTokensToBigInteger(SpunParticle<OwnedTokensParticle> value) {
		BigInteger bi = UInt256s.toBigInteger(value.getParticle().getAmount());
		return (value.getSpin() == Spin.UP) ? bi : bi.negate();
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
		return "Atom (" + this.getHid().toString() + ")";
	}

	public Map<String, String> getMetaData() {
		return this.metaData;
	}
}
