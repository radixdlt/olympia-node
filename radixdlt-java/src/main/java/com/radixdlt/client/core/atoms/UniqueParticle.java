package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class UniqueParticle extends Particle {
	private final Payload unique;

	public UniqueParticle(Payload unique, Set<EUID> destinations, Set<ECKeyPair> owners) {
		super(destinations, owners);

		Objects.requireNonNull(unique);

		this.unique = unique;
	}

	public static UniqueParticle create(Payload unique, ECPublicKey key) {
		return new UniqueParticle(unique, Collections.singleton(key.getUID()), Collections.singleton(key.toECKeyPair()));
	}
}
