package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.AtomObservation.Type;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

public class TransitionedParticle<T extends Particle> {
	private final ParticleTransition transition;
	private final T particle;

	public TransitionedParticle(ParticleTransition transition, T particle) {
		this.transition = transition;
		this.particle = particle;
	}

	public T getParticle() {
		return particle;
	}

	public Spin getSpinTo() {
		return transition.getSpinTo();
	}

	public static <T extends Particle> TransitionedParticle<T> fromSpunParticle(
		SpunParticle<T> s, AtomObservation.Type observationType
	) {
		final boolean revert = observationType == Type.DELETE;
		ParticleTransition transition = ParticleTransition.to(s.getSpin(), revert);
		return new TransitionedParticle<>(transition, s.getParticle());
	}

	public static <T extends Particle> TransitionedParticle<T> n2u(T particle) {
		return new TransitionedParticle<>(ParticleTransition.N2U, particle);
	}
}
