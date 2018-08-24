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
	private List<Particle> particles = new ArrayList<>();
	private EncryptorParticle encryptor;
	private DataParticle dataParticle;

	public AtomBuilder() {
	}

	public AtomBuilder addDestination(EUID euid) {
		this.destinations.add(euid);
		return this;
	}

	public AtomBuilder addDestination(RadixAddress address) {
		return this.addDestination(address.getUID());
	}

	public AtomBuilder setDataParticle(DataParticle dataParticle) {
		this.dataParticle = dataParticle;
		return this;
	}

	public AtomBuilder setEncryptorParticle(EncryptorParticle encryptor) {
		this.encryptor = encryptor;
		return this;
	}

	public AtomBuilder addParticle(Particle particle) {
		this.particles.add(particle);
		this.destinations.addAll(particle.getDestinations());
		return this;
	}

	public <T extends Particle> AtomBuilder addParticles(List<T> particles) {
		this.particles.addAll(particles);
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
		this.addParticle(fee);

		return this.build(timestamp);
	}

	public UnsignedAtom build(long timestamp) {
		return new UnsignedAtom(new Atom(dataParticle, particles, destinations, encryptor, timestamp));
	}

	// Temporary method for testing
	public UnsignedAtom build() {
		return this.build(System.currentTimeMillis());
	}
}
