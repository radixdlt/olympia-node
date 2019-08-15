package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;

/**
 * Transition definition from RRI to the first of two combined particles
 */
public final class RRIResourceCombinedPrimaryCreation<T extends Particle> implements TransitionProcedure<RRIParticle, T> {
	@Override
	public ProcedureResult execute(
		RRIParticle inputParticle,
		Object inputUsed,
		T outputParticle,
		Object outputUsed
	) {
		if (inputUsed != null || outputUsed != null) {
			return ProcedureResult.error("Expecting non previously used");
		}

		return ProcedureResult.popOutput(outputParticle);
	}
}
