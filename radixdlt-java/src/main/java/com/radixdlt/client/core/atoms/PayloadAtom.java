package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.Encryptor;
import java.util.List;
import java.util.Set;

public abstract class PayloadAtom extends Atom {
	private final Payload encrypted;
	private final Encryptor encryptor;

	PayloadAtom(Set<EUID> destinations, Payload encrypted, long timestamp, EUID signatureId, ECSignature signature) {
		super(destinations, timestamp, signatureId, signature);
		this.encrypted = encrypted;
		this.encryptor = null;
	}

	PayloadAtom(
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
	}

	PayloadAtom(List<Particle> particles, Set<EUID> destinations, Payload encrypted, long timestamp, EUID signatureId, ECSignature signature) {
		super(particles, destinations, timestamp, signatureId, signature);
		this.encrypted = encrypted;
		this.encryptor = null;
	}

	PayloadAtom(List<Particle> particles, Set<EUID> destinations, Payload encrypted, Encryptor encryptor, long timestamp) {
		super(destinations, particles, timestamp);
		this.encrypted = encrypted;
		this.encryptor = encryptor;
	}


	PayloadAtom(Set<EUID> destinations, Payload encrypted, List<Particle> particles, long timestamp) {
		super(destinations, particles, timestamp);
		this.encrypted = encrypted;
		this.encryptor = null;
	}

	public Encryptor getEncryptor() {
		return encryptor;
	}

	public Payload getPayload() {
		return encrypted;
	}

	public EncryptedPayload getEncryptedPayload() {
		if (encrypted == null) {
			return null;
		}

		if (encryptor == null) {
			return new EncryptedPayload(encrypted);
		} else {
			return new EncryptedPayload(encrypted, encryptor);
		}
	}
}
