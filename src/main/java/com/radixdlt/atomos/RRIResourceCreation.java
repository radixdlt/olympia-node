package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Transition definition from RRI to an RRI-enabled particle
 */
public final class RRIResourceCreation<T extends Particle> implements TransitionProcedure<RRIParticle, T> {
	private final Function<T, RRI> rriMapper;

	RRIResourceCreation(Function<T, RRI> rriMapper) {
		this.rriMapper = rriMapper;
	}

	@Override
	public ProcedureResult execute(
		RRIParticle inputParticle,
		AtomicReference<Object> inputData,
		T outputParticle,
		AtomicReference<Object> outputData
	) {
		if (!rriMapper.apply(outputParticle).equals(inputParticle.getRri())) {
			return ProcedureResult.ERROR;
		}

		return ProcedureResult.POP_INPUT_OUTPUT;
	}
}
