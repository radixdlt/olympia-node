package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.ArrayList;
import java.util.List;

public class AtomBuilder {
	private List<AbstractConsumable> consumables = new ArrayList<>();
	private List<Consumer> consumers = new ArrayList<>();
	private List<DataParticle> dataParticles = new ArrayList<>();
	private UniqueParticle uniqueParticle;

	public AtomBuilder() {
	}

	public AtomBuilder setUniqueParticle(UniqueParticle uniqueParticle) {
		this.uniqueParticle = uniqueParticle;
		return this;
	}

	public AtomBuilder addDataParticle(DataParticle dataParticle) {
		this.dataParticles.add(dataParticle);
		return this;
	}

	public AtomBuilder addConsumer(Consumer consumer) {
		this.consumers.add(consumer);
		return this;
	}

	public AtomBuilder addConsumable(Consumable consumable) {
		this.consumables.add(consumable);
		return this;
	}

	public <T extends Consumable> AtomBuilder addConsumables(List<T> particles) {
		this.consumables.addAll(particles);
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
			dataParticles.isEmpty() ? null : dataParticles,
			consumers.isEmpty() ? null : consumers, // Pretty nasty hack here. Need to fix.
			consumables,
			uniqueParticle,
			null,
			timestamp
		));
	}

	// Temporary method for testing
	public UnsignedAtom build() {
		return this.build(System.currentTimeMillis());
	}
}
