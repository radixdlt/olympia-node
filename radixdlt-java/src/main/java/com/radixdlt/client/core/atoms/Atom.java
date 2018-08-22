package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.Encryptor;
import com.radixdlt.client.core.serialization.Dson;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class Atom {
	private Set<EUID> destinations;
	private final Map<String, Long> timestamps;
	private String action;
	private final List<Particle> particles;
	private final Map<String, ECSignature> signatures;
	private transient Map<String, Long> debug = new HashMap<>();
	private final String applicationId;
	private final Payload bytes;
	private final Encryptor encryptor;

	public Atom(
		String applicationId,
		List<Particle> particles,
		Set<EUID> destinations,
		Payload bytes,
		Encryptor encryptor,
		long timestamp
	) {
		this.applicationId = applicationId;
		this.particles = particles;
		this.destinations = destinations;
		this.bytes = bytes;
		this.encryptor = encryptor;
		this.timestamps = Collections.singletonMap("default", timestamp);
		this.signatures = null;
		this.action = "STORE";
	}

	public Atom(
		String applicationId,
		List<Particle> particles,
		Set<EUID> destinations,
		Payload bytes,
		Encryptor encryptor,
		long timestamp,
		EUID signatureId,
		ECSignature signature
	) {
		this.applicationId = applicationId;
		this.particles = particles;
		this.destinations = destinations;
		this.bytes = bytes;
		this.encryptor = encryptor;
		this.timestamps = Collections.singletonMap("default", timestamp);
		this.signatures = Collections.singletonMap(signatureId.toString(), signature);
		this.action = "STORE";
	}

	public String getAction() {
		return action;
	}

	public Set<EUID> getDestinations() {
		return destinations;
	}

	public Set<Long> getShards() {
		return destinations.stream().map(EUID::getShard).collect(Collectors.toSet());
	}

	// HACK
	public Set<Long> getRequiredFirstShard() {
		if (this.particles != null
			&& this.particles.stream().anyMatch(Particle::isConsumer)
		) {
			return particles.stream()
				.filter(Particle::isConsumer)
				.flatMap(particle -> particle.getDestinations().stream())
				.map(EUID::getShard)
				.collect(Collectors.toSet());
		} else {
			return getShards();
		}
	}

	public Long getTimestamp() {
		return timestamps.get("default");
	}

	public Map<String, ECSignature> getSignatures() {
		return signatures;
	}

	public Optional<ECSignature> getSignature(EUID uid) {
		return Optional.ofNullable(signatures).map(sigs -> sigs.get(uid.toString()));
	}

	public List<Particle> getParticles() {
		return particles == null ? Collections.emptyList() : Collections.unmodifiableList(particles);
	}

	public byte[] toDson() {
		return Dson.getInstance().toDson(this);
	}

	public RadixHash getHash() {
		return RadixHash.of(Dson.getInstance().toDson(this));
	}

	public EUID getHid() {
		return getHash().toEUID();
	}

	public Encryptor getEncryptor() {
		return encryptor;
	}

	public Payload getPayload() {
		return bytes;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public List<Consumable> getConsumables() {
		return getParticles().stream()
			.filter(Particle::isConsumable)
			.map(Particle::getAsConsumable)
			.collect(Collectors.toList());
	}

	public List<Consumer> getConsumers() {
		return getParticles().stream()
			.filter(Particle::isConsumer)
			.map(Particle::getAsConsumer)
			.collect(Collectors.toList());
	}


	public Map<Set<ECPublicKey>, Map<EUID, Long>> summary() {
		return getParticles().stream()
			.filter(Particle::isAbstractConsumable)
			.map(Particle::getAsAbstractConsumable)
			.collect(Collectors.groupingBy(
				AbstractConsumable::getOwnersPublicKeys,
				Collectors.groupingBy(
					AbstractConsumable::getAssetId,
					Collectors.summingLong(AbstractConsumable::getSignedQuantity)
				)
			));
	}

	public Map<Set<ECPublicKey>, Map<EUID, List<Long>>> consumableSummary() {
		return getParticles().stream()
			.filter(Particle::isAbstractConsumable)
			.map(Particle::getAsAbstractConsumable)
			.collect(Collectors.groupingBy(
				AbstractConsumable::getOwnersPublicKeys,
				Collectors.groupingBy(
					AbstractConsumable::getAssetId,
					Collectors.mapping(AbstractConsumable::getSignedQuantity, Collectors.toList())
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
		return "Atom hid(" + getHid().toString() + ") destinations(" + destinations + ") particles(" + particles.size() + ")";
	}
}
