package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.particles.Spin;

/**
 * Represents an action in the particle state machine. Includes the main instructions
 * (N2U, U2D) and their reverts respectively (U2N, D2U).
 */
public enum ParticleTransition {
	N2U(Spin.NEUTRAL, Spin.UP),
	U2D(Spin.UP, Spin.DOWN),
	D2U(U2D),
	U2N(N2U);

	private final Spin from;
	private final Spin to;
	private final boolean isRevert;

	ParticleTransition(Spin from, Spin to) {
		this.from = from;
		this.to = to;
		this.isRevert = false;
	}

	ParticleTransition(ParticleTransition revertOf) {
		this.from = revertOf.to;
		this.to = revertOf.from;
		this.isRevert = true;
	}

	public ParticleTransition revert() {
		switch(this) {
			case N2U: return U2N;
			case U2D: return D2U;
			case U2N: return N2U;
			case D2U: return U2D;
			default:
				throw new IllegalStateException();
		}
	}

	public Spin getSpinTo() {
		return to;
	}

	public Spin getSpinFrom() {
		return from;
	}

	public static ParticleTransition to(Spin to, boolean revert) {
		for (ParticleTransition transition : values()) {
			if (transition.to.equals(to) && !transition.isRevert) {
				if (revert) {
					return transition.revert();
				} else {
					return transition;
				}
			}
		}

		throw new IllegalArgumentException("No transition available to " + to);
	}
}
