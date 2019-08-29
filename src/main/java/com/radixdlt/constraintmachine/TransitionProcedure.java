package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.Result;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 */
public interface TransitionProcedure<I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> {
	Result precondition(
		I inputParticle, N inputUsed,
		O outputParticle, U outputUsed
	);

	UsedCompute<I, N, O, U> inputUsedCompute();
	UsedCompute<I, N, O, U> outputUsedCompute();

	WitnessValidator<I> inputWitnessValidator();
	WitnessValidator<O> outputWitnessValidator();
}
