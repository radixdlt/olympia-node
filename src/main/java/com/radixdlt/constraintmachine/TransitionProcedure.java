package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.Result;
import java.util.Optional;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 */
public interface TransitionProcedure<I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> {
	Result precondition(
		I inputParticle, N inputUsed,
		O outputParticle, U outputUsed
	);

	Optional<UsedData> inputUsed(
		I inputParticle, N inputUsed,
		O outputParticle, U outputUsed
	);

	WitnessValidator<I> inputWitnessValidator();

	Optional<UsedData> outputUsed(
		I inputParticle, N inputUsed,
		O outputParticle, U outputUsed
	);

	WitnessValidator<O> outputWitnessValidator();
}
