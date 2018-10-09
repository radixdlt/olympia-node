package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.ArrayList;
import java.util.List;

public class AtomBuilder {
	private static final int POW_LEADING_ZEROES_REQUIRED = 16;
	private List<Consumable> consumables = new ArrayList<>();
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
		AtomFeeConsumable fee = new AtomFeeConsumableBuilder()
			.atom(unsignedAtom)
			.owner(owner)
			.pow(magic, POW_LEADING_ZEROES_REQUIRED)
			.build();
		this.addConsumable(fee);

		return this.build(timestamp);
	}

	public UnsignedAtom build(long timestamp) {
		List<Particle> particles = new ArrayList<>();
		particles.addAll(dataParticles);
		particles.addAll(consumables);
		if (uniqueParticle != null) {
			particles.add(uniqueParticle);
		}
		particles.add(new ChronoParticle(timestamp));
		return new UnsignedAtom(new Atom(particles));
	}

	// Temporary method for testing
	public UnsignedAtom build() {
		return this.build(System.currentTimeMillis());
	}
}
