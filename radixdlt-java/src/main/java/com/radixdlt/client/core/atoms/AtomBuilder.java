package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AtomBuilder {
	private static final int MAX_PAYLOAD_SIZE = 1028;

	private Set<EUID> destinations = new HashSet<>();
	private List<Particle> particles = new ArrayList<>();
	private ECSignature signature;
	private EUID signatureId;
	private Long timestamp;
	private ECKeyPair sharedKey;
	private String applicationId;
	private byte[] payloadRaw;
	private Class<? extends Atom> atomClass;
	private Encryptor encryptor;
	private Payload payload;

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

	public <T extends Atom> AtomBuilder type(Class<T> atomClass) {
		this.atomClass = atomClass;
		return this;
	}

	public UnsignedAtom buildWithPOWFee(int magic, ECPublicKey owner) {
		// Expensive but fine for now
		UnsignedAtom unsignedAtom = this.build();
		int size = unsignedAtom.getRawAtom().toDson().length;

		AtomFeeConsumable fee = new AtomFeeConsumableBuilder()
			.atom(unsignedAtom)
			.owner(owner)
			.pow(magic, (int) Math.ceil(Math.log(size * 8.0)))
			.build();

		this.addParticle(fee);

		return this.build();
	}

	public UnsignedAtom build() {
		Objects.requireNonNull(atomClass);

		if (this.timestamp == null) {
			this.timestamp = System.currentTimeMillis();
		}

		if (this.payloadRaw != null) {
			this.payload = new Payload(this.payloadRaw);
		}

		// TODO: add this check to when payloadRaw is first set
		if (payload != null && payload.length() > MAX_PAYLOAD_SIZE) {
			throw new IllegalStateException("Payload must be under " + MAX_PAYLOAD_SIZE + " bytes but was " + payload.length());
		}

		final Atom atom;
		if (TransactionAtom.class.isAssignableFrom(atomClass)) {
			atom = new TransactionAtom(applicationId, particles, destinations, payload, encryptor, this.timestamp);
		} else {
			throw new IllegalStateException("Unable to create atom with class: " + atomClass.getSimpleName());
		}

		return new UnsignedAtom(atom);
	}
}
