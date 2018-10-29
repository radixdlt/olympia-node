package com.radixdlt.client.core.atoms;

import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
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
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;
import org.radix.serialization2.client.Serialize;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.atommodel.storage.StorageParticle;
import com.radixdlt.client.atommodel.timestamp.TimestampParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;

/**
 * An atom is the fundamental atomic unit of storage on the ledger (similar to a block
 * in a blockchain) and defines the actions that can be issued onto the ledger.
 */
@SerializerId2("ATOM")
public final class Atom extends SerializableObject {

	@JsonProperty("particles")
	@DsonOutput(DsonOutput.Output.ALL)
	private List<SpunParticle> particles;

	@JsonProperty("signatures")
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private Map<String, ECSignature> signatures;

	private transient Map<String, Long> debug = new HashMap<>();

	private Atom() {
	}

	public Atom(List<SpunParticle> particles) {
		this.particles = particles;
		this.signatures = null;
	}

	private Atom(
		List<SpunParticle> particles,
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

	public List<SpunParticle> getSpunParticles() {
		return particles != null ? particles : Collections.emptyList();
	}

	private Set<Long> getShards() {
		return getSpunParticles().stream()
			.map(SpunParticle<Particle>::getParticle)
			.map(Particle::getAddresses)
			.flatMap(Set::stream)
			.map(ECPublicKey::getUID)
			.map(EUID::getShard)
			.collect(Collectors.toSet());
	}

	// HACK
	public Set<Long> getRequiredFirstShard() {
		if (this.particles.stream().anyMatch(s -> s.getSpin() == Spin.DOWN)) {
			return particles.stream()
				.filter(s -> s.getSpin() == Spin.DOWN)
				.flatMap(s -> s.getParticle().getAddresses().stream())
				.map(ECPublicKey::getUID)
				.map(EUID::getShard)
				.collect(Collectors.toSet());
		} else {
			return getShards();
		}
	}

	public Stream<Particle> particles(Spin spin) {
		return particles.stream().filter(s -> s.getSpin() == spin).map(SpunParticle::getParticle);
	}

	public Stream<ECPublicKey> addresses() {
		return particles.stream()
			.map(SpunParticle<Particle>::getParticle)
			.map(Particle::getAddresses)
			.flatMap(Set::stream);
	}

	public Long getTimestamp() {
		return this.getSpunParticles().stream()
			.map(SpunParticle::getParticle)
			.filter(p -> p instanceof TimestampParticle)
			.map(p -> ((TimestampParticle) p).getTimestamp()).findAny()
			.orElse(0L);
	}

	public Map<String, ECSignature> getSignatures() {
		return signatures;
	}

	public Optional<ECSignature> getSignature(EUID uid) {
		return Optional.ofNullable(signatures).map(sigs -> sigs.get(uid.toString()));
	}

	public Stream<SpunParticle<OwnedTokensParticle>> consumables() {
		return this.getSpunParticles().stream()
			.filter(s -> s.getParticle() instanceof OwnedTokensParticle)
			.map(s -> (SpunParticle<OwnedTokensParticle>) s);
	}

	public List<OwnedTokensParticle> getConsumables(Spin spin) {
		return this.getSpunParticles().stream()
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
		return RadixHash.of(Serialize.getInstance().toDson(this, DsonOutput.Output.HASH));
	}

	public EUID getHid() {
		return getHash().toEUID();
	}

	public List<StorageParticle> getDataParticles() {
		return this.getSpunParticles().stream()
			.map(SpunParticle::getParticle)
			.filter(p -> p instanceof StorageParticle)
			.map(p -> (StorageParticle) p)
			.collect(Collectors.toList());
	}

	public Map<TokenClassReference, Map<ECPublicKey, Long>> tokenSummary() {
		return consumables()
			.collect(Collectors.groupingBy(
				s -> s.getParticle().getTokenClassReference(),
				Collectors.groupingBy(
					s -> s.getParticle().getOwner(),
					Collectors.summingLong((SpunParticle<OwnedTokensParticle> value) ->
						(value.getSpin() == Spin.UP ? 1 : -1) * value.getParticle().getAmount()
					)
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
