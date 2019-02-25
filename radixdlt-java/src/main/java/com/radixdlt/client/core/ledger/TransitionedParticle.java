package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.AtomObservation.Type;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

/**
 * A transitioned particle represents an action/instruction which higher layer protocols use
 * to update their state. It differs from a SpunParticle in that TransitionedParticles include
 * the ability to revert previous SpunParticles, thus the particle state machine complexity
 * increases by a factor of exactly 2.
 *
 * @param <T> the type of particle
 */
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

	public Spin getSpinFrom() {
		return transition.getSpinFrom();
	}

	public ParticleTransition getTransition() {
		return transition;
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

	public static <T extends Particle> TransitionedParticle<T> u2n(T particle) {
		return new TransitionedParticle<>(ParticleTransition.U2N, particle);
	}

	public static <T extends Particle> TransitionedParticle<T> u2d(T particle) {
		return new TransitionedParticle<>(ParticleTransition.U2D, particle);
	}

	public static <T extends Particle> TransitionedParticle<T> d2u(T particle) {
		return new TransitionedParticle<>(ParticleTransition.D2U, particle);
	}
}
