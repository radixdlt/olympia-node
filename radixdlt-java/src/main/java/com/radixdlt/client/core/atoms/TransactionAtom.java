package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.Encryptor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionAtom extends PayloadAtom {
	private final String operation = "TRANSFER";

	TransactionAtom(List<Particle> particles, Set<EUID> destinations, Payload payload, long timestamp) {
		super(destinations, payload, particles, timestamp);
	}

	TransactionAtom(List<Particle> particles, Set<EUID> destinations, Payload payload, Encryptor encryptor, long timestamp) {
		super(particles, destinations, payload, encryptor, timestamp);
	}

	TransactionAtom(List<Particle> particles, Set<EUID> destinations, long timestamp) {
		super(destinations, null, particles, timestamp);
	}

	TransactionAtom(List<Particle> particles, Set<EUID> destinations, Payload payload, Encryptor encryptor, EUID signatureId, ECSignature signature, long timestamp) {
		super(particles, destinations, payload, encryptor, timestamp, signatureId, signature);
	}

	public List<Consumable> getConsumables() {
		return particles.stream()
			.filter(Particle::isConsumable)
			.map(Particle::getAsConsumable)
			.collect(Collectors.toList());
	}

	public List<Consumer> getConsumers() {
		return particles.stream()
			.filter(Particle::isConsumer)
			.map(Particle::getAsConsumer)
			.collect(Collectors.toList());
	}


	public Map<Set<ECPublicKey>,Map<EUID,Long>> summary() {
		return particles.stream()
			.filter(Particle::isAbstractConsumable)
			.map(Particle::getAsAbstractConsumable)
			.collect(Collectors.groupingBy(
				AbstractConsumable::getOwners,
				Collectors.groupingBy(
					AbstractConsumable::getAssetId,
					Collectors.summingLong(AbstractConsumable::signedQuantity)
				)
			))
			;
	}

	public Map<Set<ECPublicKey>,Map<EUID,List<Long>>> consumableSummary() {
		return particles.stream()
		.filter(Particle::isAbstractConsumable)
		.map(Particle::getAsAbstractConsumable)
		.collect(Collectors.groupingBy(
			AbstractConsumable::getOwners,
			Collectors.groupingBy(
				AbstractConsumable::getAssetId,
				Collectors.mapping(AbstractConsumable::signedQuantity, Collectors.toList())
			)
		))
		;
	}

	@Override
	public String toString() {
		return super.toString() + " " + consumableSummary();
	}
}
