package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class RRIResourceCombinedDependentCreation<T extends Particle, U extends Particle> implements TransitionProcedure<RRIParticle, U> {
	private final Class<T> particleClass0;
	private final Function<U, RRI> rriMapper1;
	private final BiPredicate<T, U> combinedCheck;

	public RRIResourceCombinedDependentCreation(
		Class<T> particleClass0,
		Function<U, RRI> rriMapper1,
		BiPredicate<T, U> combinedCheck
	) {
		this.particleClass0 = particleClass0;
		this.rriMapper1 = rriMapper1;
		this.combinedCheck = combinedCheck;
	}

	@Override
	public ProcedureResult execute(
		RRIParticle inputParticle,
		AtomicReference<Object> inputData,
		U outputParticle,
		AtomicReference<Object> outputData
	) {
		if (inputData.get() == null || !inputData.get().getClass().equals(particleClass0)) {
			return ProcedureResult.ERROR;
		}

		if (!rriMapper1.apply(outputParticle).equals(inputParticle.getRri())) {
			return ProcedureResult.ERROR;
		}

		if (!combinedCheck.test((T) inputData.get(), outputParticle)) {
			return ProcedureResult.ERROR;
		}
		return ProcedureResult.POP_INPUT_OUTPUT;
	}
}
