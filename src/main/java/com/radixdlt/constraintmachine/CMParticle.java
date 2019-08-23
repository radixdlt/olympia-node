package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;

/**
 * A particle to be processed by a Constraint Machine
 */
public final class CMParticle {
	private final Particle particle;
	private final DataPointer firstDataPointer;
	private final Spin checkSpin;

	public CMParticle(
		Particle particle,
		DataPointer firstDataPointer,
		Spin checkSpin
	) {
		this.particle = particle;
		this.firstDataPointer = firstDataPointer;
		this.checkSpin = checkSpin;
	}

	public Particle getParticle() {
		return particle;
	}

	public DataPointer getDataPointer() {
		return firstDataPointer;
	}

	public Spin getCheckSpin() {
		return checkSpin;
	}
}
