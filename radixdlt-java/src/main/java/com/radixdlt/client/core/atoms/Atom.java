package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.serialization.Dson;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Atom {
	private Set<EUID> destinations;
	private final Map<String, Long> timestamps;
	private String action;
	private final List<Particle> particles;
	private final Map<String, ECSignature> signatures;
	private transient Map<String, Long> debug = new HashMap<>();

	Atom() {
		this.destinations = Collections.emptySet();
		this.timestamps = null;
		this.particles = null;
		this.signatures = null;
		this.action = "STORE";
	}

	Atom(Set<EUID> destinations, long timestamp, EUID signatureId, ECSignature signature) {
		this.destinations = destinations;
		this.particles = null;
		this.timestamps = Collections.singletonMap("default", timestamp);
		this.action = "STORE";
		// HACK
		// TODO: fix this
		this.signatures = signatureId == null ? null : Collections.singletonMap(signatureId.toString(), signature);
	}

	Atom(Set<EUID> destinations, List<Particle> particles, long timestamp) {
		this.destinations = destinations;
		this.particles = particles;
		this.timestamps = Collections.singletonMap("default", timestamp);
		this.signatures = null;
		this.action = "STORE";
	}

	Atom(List<Particle> particles, Set<EUID> destinations, long timestamp, EUID signatureId, ECSignature signature) {
		this.destinations = destinations;
		this.particles = particles;
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

	public boolean isUnknown() {
		return this.getClass() == UnknownAtom.class;
	}

	public boolean isNullAtom() {
		return this.getClass() == NullAtom.class;
	}

	public boolean isMessageAtom() {
		return this.getClass() == ApplicationPayloadAtom.class;
	}

	public boolean isTransactionAtom() {
		return this.getClass() == TransactionAtom.class;
	}

	public NullAtom getAsNullAtom() {
		return (NullAtom) this;
	}

	public ApplicationPayloadAtom getAsMessageAtom() {
		return (ApplicationPayloadAtom) this;
	}

	public TransactionAtom getAsTransactionAtom() {
		return (TransactionAtom) this;
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
		return "Atom hid(" + getHid().toString() + ") destinations(" + destinations + ")";
	}
}
