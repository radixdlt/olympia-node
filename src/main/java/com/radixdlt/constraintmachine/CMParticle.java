package com.radixdlt.constraintmachine;

import com.radixdlt.store.SpinStateMachine;
import java.util.stream.Stream;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;

/**
 * A particle processed by a Constraint Machine
 */
public class CMParticle {
	private final Particle particle;
	private final DataPointer firstDataPointer;
	private final Spin checkSpin;
	private final int numPushes;

	public CMParticle(
		Particle particle,
		DataPointer firstDataPointer,
		Spin checkSpin,
		int numPushes
	) {
		this.particle = particle;
		this.firstDataPointer = firstDataPointer;
		this.checkSpin = checkSpin;
		this.numPushes = numPushes;
	}

	public Particle getParticle() {
		return particle;
	}

	public Stream<Spin> nextSpins() {
		return Stream.iterate(checkSpin, SpinStateMachine::next)
			.skip(1)
			.limit(numPushes);
	}

	public DataPointer getDataPointer() {
		return firstDataPointer;
	}

	public Spin getNextSpin() {
		return SpinStateMachine.next(checkSpin);
	}
}
