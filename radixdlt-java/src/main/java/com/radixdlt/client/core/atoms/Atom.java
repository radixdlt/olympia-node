package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.serialization.Dson;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An atom is the fundamental atomic unit of storage on the ledger (similar to a block
 * in a blockchain) and defines the actions that can be issued onto the ledger.
 */
public final class Atom {
	// TODO: These will be turned into a list of DeleteParticles in the future
	private final List<Consumer> consumers;

	// TODO: These will be turned into a list of CreateParticles in the future
	private final List<Particle> particles;

	private final Map<String, ECSignature> signatures;

	private transient Map<String, Long> debug = new HashMap<>();

	public Atom(List<Particle> particles, List<Consumer> consumers) {
		this.particles = particles;
		this.consumers = consumers;
		this.signatures = null;
	}

	private Atom(
		List<Particle> particles, List<Consumer> consumers,
		EUID signatureId,
		ECSignature signature
	) {
		this.particles = particles;
		this.consumers = consumers;
		this.signatures = Collections.singletonMap(signatureId.toString(), signature);
	}

	public Atom withSignature(ECSignature signature, EUID signatureId) {
		return new Atom(
			particles,
			consumers,
			signatureId,
			signature
		);
	}

	private List<Particle> getParticles() {
		return particles != null ? particles : Collections.emptyList();
	}

	public Set<Long> getShards() {
		return consumers.stream()
			.map(Consumer::getOwners)
			.flatMap(Set::stream)
			.map(ECKeyPair::getUID)
			.map(EUID::getShard)
			.collect(Collectors.toSet());
	}

	// HACK
	public Set<Long> getRequiredFirstShard() {
		if (this.consumers != null && !this.consumers.isEmpty()) {
			return consumers.stream()
				.flatMap(consumer -> consumer.getDestinations().stream())
				.map(EUID::getShard)
				.collect(Collectors.toSet());
		} else {
			return getShards();
		}
	}


	public Long getTimestamp() {
		return this.getParticles().stream()
			.filter(p -> p instanceof ChronoParticle)
			.map(p -> ((ChronoParticle) p).getTimestamp()).findAny()
			.orElse(0L);
	}

	public Map<String, ECSignature> getSignatures() {
		return signatures;
	}

	public Optional<ECSignature> getSignature(EUID uid) {
		return Optional.ofNullable(signatures).map(sigs -> sigs.get(uid.toString()));
	}

	public List<Consumer> getConsumers() {
		return consumers == null ? Collections.emptyList() : Collections.unmodifiableList(consumers);
	}

	public List<AbstractConsumable> getConsumables() {
		return this.getParticles().stream()
			.filter(p -> p instanceof AbstractConsumable)
			.map(p -> (AbstractConsumable)p)
			.collect(Collectors.toList());
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

	public List<DataParticle> getDataParticles() {
		return this.getParticles().stream()
			.filter(p -> p instanceof DataParticle)
			.map(p -> (DataParticle)p)
			.collect(Collectors.toList());
	}

	public Map<Set<ECPublicKey>, Map<EUID, Long>> summary() {
		return Stream.concat(getConsumers().stream(), getConsumables().stream())
			.collect(Collectors.groupingBy(
				AbstractConsumable::getOwnersPublicKeys,
				Collectors.groupingBy(
					AbstractConsumable::getAssetId,
					Collectors.summingLong(AbstractConsumable::getSignedQuantity)
				)
			));
	}

	public Map<Set<ECPublicKey>, Map<EUID, List<Long>>> consumableSummary() {
		return Stream.concat(getConsumers().stream(), getConsumables().stream())
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
		return "Atom hid(" + getHid().toString() + ")";
	}
}
