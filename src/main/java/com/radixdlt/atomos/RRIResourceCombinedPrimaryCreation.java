package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Transition definition from RRI to the first of two combined particles
 */
public final class RRIResourceCombinedPrimaryCreation<T extends Particle, U extends Particle> implements TransitionProcedure<RRIParticle, T> {
	private final Function<T, RRI> rriMapper0;

	RRIResourceCombinedPrimaryCreation(Function<T, RRI> rriMapper0) {
		this.rriMapper0 = rriMapper0;
	}

	@Override
	public ProcedureResult execute(
		RRIParticle inputParticle,
		AtomicReference<Object> inputData,
		T outputParticle,
		AtomicReference<Object> outputData
	) {
		if (!rriMapper0.apply(outputParticle).equals(inputParticle.getRri())) {
			return new ProcedureResult(CMAction.ERROR);
		}

		inputData.set(outputParticle);
		return new ProcedureResult(CMAction.POP_OUTPUT);
	}
}
