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
		Object inputUsed,
		T outputParticle,
		Object outputUsed
	) {
		return ProcedureResult.popOutput(null);
	}
}
