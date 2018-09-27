package com.radixdlt.client.core.atoms;

import java.util.Collections;
import java.util.Map;

/**
 * Particle which stores time related aspects of an atom.
 */
public class ChronoParticle extends Particle {
	private final Map<String, Long> timestamps;

	public ChronoParticle(long timestamp) {
		super(1);

		this.timestamps = Collections.singletonMap("default", timestamp);
	}

	public Long getTimestamp() {
		return timestamps.get("default");
	}
}
