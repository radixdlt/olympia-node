package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.Encryptor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PayloadAtom extends Atom {
	private final String operation = "TRANSFER";
	private final String applicationId;
	private final Payload encrypted;
	private final Encryptor encryptor;

	PayloadAtom(
		String applicationId,
		Set<EUID> destinations,
		Payload encrypted,
		long timestamp,
		EUID signatureId,
		ECSignature signature
	) {
		super(destinations, timestamp, signatureId, signature);
		this.encrypted = encrypted;
		this.encryptor = null;
		this.applicationId = applicationId;
	}

	public PayloadAtom(
		String applicationId,
		List<Particle> particles,
		Set<EUID> destinations,
		Payload encrypted,
		Encryptor encryptor,
		long timestamp,
		EUID signatureId,
		ECSignature signature
	) {
		super(particles, destinations, timestamp, signatureId, signature);
		this.encrypted = encrypted;
		this.encryptor = encryptor;
		this.applicationId = applicationId;
	}

	PayloadAtom(
		String applicationId,
		List<Particle> particles,
		Set<EUID> destinations,
		Payload encrypted,
		long timestamp,
		EUID signatureId,
		ECSignature signature
	) {
		super(particles, destinations, timestamp, signatureId, signature);
		this.encrypted = encrypted;
		this.encryptor = null;
		this.applicationId = applicationId;
	}

	public PayloadAtom(
		String applicationId,
		List<Particle> particles,
		Set<EUID> destinations,
		Payload encrypted,
		Encryptor encryptor,
		long timestamp
	) {
		super(destinations, particles, timestamp);
		this.encrypted = encrypted;
		this.encryptor = encryptor;
		this.applicationId = applicationId;
	}


	PayloadAtom(
		String applicationId,
		Set<EUID> destinations,
		Payload encrypted,
		List<Particle> particles,
		long timestamp
	) {
		super(destinations, particles, timestamp);
		this.encrypted = encrypted;
		this.encryptor = null;
		this.applicationId = applicationId;
	}

	public Encryptor getEncryptor() {
		return encryptor;
	}

	public Payload getPayload() {
		return encrypted;
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
}
