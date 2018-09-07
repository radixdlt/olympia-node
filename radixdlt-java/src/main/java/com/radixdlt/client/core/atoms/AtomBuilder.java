package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AtomBuilder {
	private Set<EUID> destinations = new HashSet<>();
	private List<AbstractConsumable> consumables = new ArrayList<>();
	private List<Consumer> consumers = new ArrayList<>();
	private EncryptorParticle encryptor;
	private DataParticle dataParticle;
	private UniqueParticle uniqueParticle;

	public AtomBuilder() {
	}

	public AtomBuilder addDestination(EUID euid) {
		this.destinations.add(euid);
		return this;
	}

	public AtomBuilder addDestination(RadixAddress address) {
		return this.addDestination(address.getUID());
	}

	public AtomBuilder setUniqueParticle(UniqueParticle uniqueParticle) {
		this.uniqueParticle = uniqueParticle;
		return this;
	}

	public AtomBuilder setDataParticle(DataParticle dataParticle) {
		this.dataParticle = dataParticle;
		return this;
	}

	public AtomBuilder setEncryptorParticle(EncryptorParticle encryptor) {
		this.encryptor = encryptor;
		return this;
	}

	public AtomBuilder addConsumer(Consumer consumer) {
		this.consumers.add(consumer);
		this.destinations.addAll(consumer.getDestinations());
		return this;
	}

	public AtomBuilder addConsumable(Consumable consumable) {
		this.consumables.add(consumable);
		this.destinations.addAll(consumable.getDestinations());
		return this;
	}

	public <T extends Consumable> AtomBuilder addConsumables(List<T> particles) {
		this.consumables.addAll(particles);
		particles.stream().flatMap(particle -> particle.getDestinations().stream()).forEach(destinations::add);
		return this;
	}

	public UnsignedAtom buildWithPOWFee(int magic, ECPublicKey owner) {
		long timestamp = System.currentTimeMillis();

		// Expensive but fine for now
		UnsignedAtom unsignedAtom = this.build(timestamp);

		// Rebuild with atom fee
		int size = unsignedAtom.getRawAtom().toDson().length;
		AtomFeeConsumable fee = new AtomFeeConsumableBuilder()
			.atom(unsignedAtom)
			.owner(owner)
			.pow(magic, (int) Math.ceil(Math.log(size * 8.0)))
			.build();
		this.addConsumable(fee);

		return this.build(timestamp);
	}

	public UnsignedAtom build(long timestamp) {
		return new UnsignedAtom(new Atom(
			dataParticle,
			consumers.isEmpty() ? null : consumers, // Pretty nasty hack here. Need to fix.
			consumables,
			destinations,
			encryptor,
			uniqueParticle,
			timestamp
		));
	}

	// Temporary method for testing
	public UnsignedAtom build() {
		return this.build(System.currentTimeMillis());
	}
}
