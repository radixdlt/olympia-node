package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Particle which stores time related aspects of an atom.
 */
public class ChronoParticle implements Particle {
	private final Map<String, Long> timestamps;
	private final Spin spin;

	public ChronoParticle(long timestamp) {
		this.spin = Spin.UP;
		this.timestamps = Collections.singletonMap("default", timestamp);
	}

	public Spin getSpin() {
		return spin;
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return Collections.emptySet();
	}

	public Long getTimestamp() {
		return timestamps.get("default");
	}
}
