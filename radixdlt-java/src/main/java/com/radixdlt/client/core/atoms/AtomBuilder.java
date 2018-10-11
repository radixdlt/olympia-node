package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.ChronoParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.ArrayList;
import java.util.List;

public class AtomBuilder {
	private static final int POW_LEADING_ZEROES_REQUIRED = 16;
	private List<Particle> particles = new ArrayList<>();

	public AtomBuilder() {
	}

	public AtomBuilder addParticles(List<Particle> particles) {
		this.particles.addAll(particles);
		return this;
	}

	public AtomBuilder addParticle(Particle particle) {
		this.particles.add(particle);
		return this;
	}

	public UnsignedAtom buildWithPOWFee(int magic, ECPublicKey owner, TokenReference powToken) {
		long timestamp = System.currentTimeMillis();

		// Expensive but fine for now
		UnsignedAtom unsignedAtom = this.build(timestamp);

		// Rebuild with atom fee
		AtomFeeConsumable fee = new AtomFeeConsumableBuilder()
			.powToken(powToken)
			.atom(unsignedAtom)
			.owner(owner)
			.pow(magic, POW_LEADING_ZEROES_REQUIRED)
			.build();
		this.addParticle(fee);

		return this.build(timestamp);
	}

	public UnsignedAtom build(long timestamp) {
		List<Particle> particles = new ArrayList<>(this.particles);
		particles.add(new ChronoParticle(timestamp));
		return new UnsignedAtom(new Atom(particles));
	}

	// Temporary method for testing
	public UnsignedAtom build() {
		return this.build(System.currentTimeMillis());
	}
}
