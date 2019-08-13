package com.radixdlt.atommodel.procedures;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Procedure which checks that payload particles
 */
public final class NonRRIResourceCreation<T extends Particle> implements TransitionProcedure<Particle, T> {
	public NonRRIResourceCreation() {
	}

	@Override
	public ProcedureResult execute(
		Particle inputParticle,
		T outputParticle,
		AtomicReference<Object> data,
		ProcedureResult prevResult
	) {
		return new ProcedureResult(CMAction.POP_OUTPUT);
	}
}
