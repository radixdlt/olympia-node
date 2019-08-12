package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class RRIResourceCombinedPrimaryCreation<T extends Particle, U extends Particle> implements TransitionProcedure<RRIParticle, T> {
	private final Function<T, RRI> rriMapper0;

	public RRIResourceCombinedPrimaryCreation(Function<T, RRI> rriMapper0) {
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
			return ProcedureResult.ERROR;
		}

		inputData.set(outputParticle);
		return ProcedureResult.POP_OUTPUT;
	}
}
