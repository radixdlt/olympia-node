package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 */
public interface TransitionProcedure<T extends Particle, U extends Particle> {
	enum ProcedureResult {
		POP_INPUT,
		POP_OUTPUT,
		POP_INPUT_OUTPUT,
		ERROR
	}

	ProcedureResult execute(
		T inputParticle,
		AtomicReference<Object> inputData,
		U outputParticle,
		AtomicReference<Object> outputData
	);
}
