package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Particle which stores time related aspects of an atom.
 */
public class ChronoParticle implements Particle {
	private final Map<String, Long> timestamps;
	private final long spin;

	public ChronoParticle(long timestamp) {
		this.spin = 1;
		this.timestamps = Collections.singletonMap("default", timestamp);
	}

	public long getSpin() {
		return spin;
	}

	public Set<EUID> getDestinations() {
		return Collections.emptySet();
	}

	public Long getTimestamp() {
		return timestamps.get("default");
	}
}
