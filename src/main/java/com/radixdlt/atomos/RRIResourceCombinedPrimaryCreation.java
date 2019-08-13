package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.function.Function;

/**
 * Transition definition from RRI to the first of two combined particles
 */
public final class RRIResourceCombinedPrimaryCreation<T extends Particle> implements TransitionProcedure<RRIParticle, T> {
	private final Function<T, RRI> rriMapper0;

	RRIResourceCombinedPrimaryCreation(Function<T, RRI> rriMapper0) {
		this.rriMapper0 = rriMapper0;
	}

	@Override
	public ProcedureResult execute(
		RRIParticle inputParticle,
		T outputParticle,
		ProcedureResult prevResult
	) {
		if (!rriMapper0.apply(outputParticle).equals(inputParticle.getRri())) {
			return new ProcedureResult(CMAction.ERROR, null);
		}

		return new ProcedureResult(CMAction.POP_OUTPUT, outputParticle);
	}
}
