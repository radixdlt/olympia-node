package com.radixdlt.atommodel.procedures;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;

/**
 * Procedure which checks that payload particles
 */
public final class NonRRIResourceCreation<T extends Particle> implements TransitionProcedure<Particle, T> {
	@Override
	public ProcedureResult execute(
		Particle inputParticle,
		T outputParticle,
		ProcedureResult prevResult
	) {
		return new ProcedureResult(CMAction.POP_OUTPUT, null);
	}
}
