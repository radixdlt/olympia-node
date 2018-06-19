package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.Encryptor;
import java.util.List;
import java.util.Set;

public class ApplicationPayloadAtom extends PayloadAtom {
	private final String applicationId;

	ApplicationPayloadAtom(String applicationId, List<Particle> particles, Set<EUID> destinations, Payload encrypted, Encryptor encryptor, long timestamp) {
		super(particles, destinations, encrypted, encryptor, timestamp);
		this.applicationId = applicationId;
	}


	ApplicationPayloadAtom(String applicationId, List<Particle> particles, Set<EUID> destinations, Payload encrypted, Encryptor encryptor, long timestamp, EUID signatureId, ECSignature signature) {
		super(particles, destinations, encrypted, encryptor, timestamp, signatureId, signature);
		this.applicationId = applicationId;
	}

	public String getApplicationId() {
		return applicationId;
	}
}
