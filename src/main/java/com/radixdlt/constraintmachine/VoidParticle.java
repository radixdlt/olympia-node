package com.radixdlt.constraintmachine;

public final class VoidParticle extends Particle {
	private VoidParticle() {
		throw new IllegalStateException("Cannot instantiate.");
	}
}
