package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class UniqueParticle implements Particle {
	private final Payload unique;
	private final Set<EUID> destinations;
	private final Set<ECKeyPair> owners;
	private final Spin spin;

	// TODO: make immutable
	public UniqueParticle(Payload unique, Set<EUID> destinations, Set<ECKeyPair> owners) {
		Objects.requireNonNull(unique);

		this.spin = Spin.UP;
		this.destinations = destinations;
		this.owners = owners;
		this.unique = unique;
	}

	public Set<EUID> getDestinations() {
		return destinations;
	}

	public Spin getSpin() {
		return spin;
	}

	public static UniqueParticle create(Payload unique, ECPublicKey key) {
		return new UniqueParticle(unique, Collections.singleton(key.getUID()), Collections.singleton(key.toECKeyPair()));
	}
}
