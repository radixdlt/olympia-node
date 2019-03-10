package com.radixdlt.client.core.ledger;

import java.util.Objects;

/**
 * A helper wrapper class for transitioned particles which can also represent HEAD events
 */
public class ParticleObservation {
	private final TransitionedParticle particle;

	private ParticleObservation(TransitionedParticle particle) {
		this.particle = particle;
	}

	public TransitionedParticle getParticle() {
		return particle;
	}

	public boolean isHead() {
		return this.particle == null;
	}

	public static ParticleObservation head() {
		return new ParticleObservation(null);
	}

	public static ParticleObservation ofParticle(TransitionedParticle particle) {
		Objects.requireNonNull(particle);

		return new ParticleObservation(particle);
	}
}
