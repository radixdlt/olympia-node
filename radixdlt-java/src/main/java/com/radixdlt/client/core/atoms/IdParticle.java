package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Collections;
import java.util.Set;

public class IdParticle extends Particle {
	private final String applicationId;
	private final EUID uniqueId;

	public IdParticle(String applicationId, EUID uniqueId, Set<EUID> destinations, Set<ECKeyPair> owners) {
		super(destinations, owners);

		this.applicationId = applicationId;
		this.uniqueId = uniqueId;
	}

	public static IdParticle create(String applicationId, EUID uniqueId, ECPublicKey key) {
		return new IdParticle(applicationId, uniqueId, Collections.singleton(key.getUID()), Collections.singleton(key.toECKeyPair()));
	}
}
