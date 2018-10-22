package com.radixdlt.client.core.atoms;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.ChronoParticle;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.DataParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;

/**
 * An atom is the fundamental atomic unit of storage on the ledger (similar to a block
 * in a blockchain) and defines the actions that can be issued onto the ledger.
 */
@SerializerId2("ATOM")
public final class Atom {
	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	// Placeholder for the serializer ID
	@JsonProperty("serializer")
	@DsonOutput({Output.API, Output.WIRE, Output.PERSIST})
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("particles")
	@DsonOutput(Output.ALL)
	private List<Particle> particles;

	@JsonProperty("signatures")
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	private Map<String, ECSignature> signatures;

	private transient Map<String, Long> debug = new HashMap<>();

	Atom() {
		// No-arg constructor for serializer
	}

	public Atom(List<Particle> particles) {
		this.particles = particles;
		this.signatures = null;
	}

	private Atom(
		List<Particle> particles,
		EUID signatureId,
		ECSignature signature
	) {
		this.particles = particles;
		this.signatures = Collections.singletonMap(signatureId.toString(), signature);
	}

	public Atom withSignature(ECSignature signature, EUID signatureId) {
		return new Atom(
			particles,
			signatureId,
			signature
		);
	}

	public List<Particle> getParticles() {
		return particles != null ? particles : Collections.emptyList();
	}

	private Set<Long> getShards() {
		return getParticles().stream()
			.map(Particle::getAddresses)
			.flatMap(Set::stream)
			.map(ECPublicKey::getUID)
			.map(EUID::getShard)
			.collect(Collectors.toSet());
	}

	// HACK
	public Set<Long> getRequiredFirstShard() {
		if (this.particles.stream().anyMatch(p -> p.getSpin() == Spin.DOWN)) {
			return particles.stream()
				.filter(p -> p.getSpin() == Spin.DOWN)
				.flatMap(consumer -> consumer.getAddresses().stream())
				.map(ECPublicKey::getUID)
				.map(EUID::getShard)
				.collect(Collectors.toSet());
		} else {
			return getShards();
		}
	}

	public Stream<Particle> particles(Spin spin) {
		return particles.stream().filter(p -> p.getSpin() == spin);
	}

	public Stream<ECPublicKey> addresses() {
		return particles.stream()
			.map(Particle::getAddresses)
			.flatMap(Set::stream);
	}

	public Long getTimestamp() {
		return this.getParticles().stream()
			.filter(p -> p instanceof ChronoParticle)
			.map(p -> ((ChronoParticle) p).getTimestamp())
			.findAny().orElse(0L);
	}

	public Map<String, ECSignature> getSignatures() {
		return signatures;
	}

	public Optional<ECSignature> getSignature(EUID uid) {
		return Optional.ofNullable(signatures).map(sigs -> sigs.get(uid.toString()));
	}

	public Stream<Consumable> consumables() {
		return this.getParticles().stream()
			.filter(p -> p instanceof Consumable)
			.map(p -> (Consumable) p);
	}

	public List<Consumable> getConsumables() {
		return this.getParticles().stream()
			.filter(p -> p instanceof Consumable)
			.map(p -> (Consumable) p)
			.collect(Collectors.toList());
	}

	public List<Consumable> getConsumables(Spin spin) {
		return this.getParticles().stream()
			.filter(p -> p instanceof Consumable)
			.filter(p -> p.getSpin() == spin)
			.map(p -> (Consumable) p)
			.collect(Collectors.toList());
	}

	public byte[] toDson() {
		return Serialize.getInstance().toDson(this, Output.HASH);
	}

	public RadixHash getHash() {
		return RadixHash.of(Serialize.getInstance().toDson(this, Output.HASH));
	}

	public EUID getHid() {
		return getHash().toEUID();
	}

	public List<DataParticle> getDataParticles() {
		return this.getParticles().stream()
			.filter(p -> p instanceof DataParticle)
			.map(p -> (DataParticle) p)
			.collect(Collectors.toList());
	}

	public Map<TokenRef, Map<ECPublicKey, Long>> tokenSummary() {
		return consumables()
			.collect(Collectors.groupingBy(
				Consumable::getTokenRef,
				Collectors.groupingBy(
					Consumable::getOwner,
					Collectors.summingLong(Consumable::getSignedAmount)
				)
			));
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Atom)) {
			return false;
		}

		Atom atom = (Atom) o;
		return getHash().equals(atom.getHash());
	}

	@Override
	public int hashCode() {
		return getHash().hashCode();
	}

	public long getDebug(String name) {
		if (debug == null) {
			debug = new HashMap<>();
		}
		return debug.get(name);
	}

	public void putDebug(String name, long value) {
		if (debug == null) {
			debug = new HashMap<>();
		}
		debug.put(name, value);
	}

	@Override
	public String toString() {
		return "Atom particles(" + getHid().toString() + ")";
	}
}
