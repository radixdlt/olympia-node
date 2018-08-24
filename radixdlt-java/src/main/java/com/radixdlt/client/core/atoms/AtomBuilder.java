package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AtomBuilder {
	private static final int MAX_PAYLOAD_SIZE = 1028;

	private Set<EUID> destinations = new HashSet<>();
	private List<Particle> particles = new ArrayList<>();
	private String applicationId;
	private byte[] payloadRaw;
	private Encryptor encryptor;

	public AtomBuilder() {
	}

	public AtomBuilder addDestination(EUID euid) {
		this.destinations.add(euid);
		return this;
	}

	public AtomBuilder addDestination(RadixAddress address) {
		return this.addDestination(address.getUID());
	}

	public AtomBuilder applicationId(String applicationId) {
		this.applicationId = applicationId;
		return this;
	}

	public AtomBuilder payload(byte[] payloadRaw) {
		this.payloadRaw = payloadRaw;
		return this;
	}

	public AtomBuilder payload(String payloadRaw) {
		return this.payload(payloadRaw.getBytes());
	}

	public AtomBuilder protectors(List<EncryptedPrivateKey> protectors) {
		this.encryptor = new Encryptor(protectors);
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
		final Payload payload;
		if (this.payloadRaw != null) {
			if (payloadRaw.length > MAX_PAYLOAD_SIZE) {
				throw new IllegalStateException("Payload must be under " + MAX_PAYLOAD_SIZE + " bytes but was " + payloadRaw.length);

			}
			payload = new Payload(this.payloadRaw);
		} else {
			payload = null;
		}

		return new UnsignedAtom(new Atom(applicationId, particles, destinations, payload, encryptor, timestamp));
	}

	// Temporary method for testing
	public UnsignedAtom build() {
		return this.build(System.currentTimeMillis());
	}
}
