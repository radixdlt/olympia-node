package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 * TODO: split transition checks and witness validator
 */
public interface ParticleProcedure {
	enum ProcedureResult {
		POP_INPUT,
		POP_OUTPUT,
		POP_INPUT_OUTPUT,
		ERROR
	}


	ProcedureResult execute(
		Particle inputParticle,
		AtomicReference<Object> inputData,
		Particle outputParticle,
		AtomicReference<Object> outputData
	);

	boolean validateWitness(
		ProcedureResult result,
		Particle inputParticle,
		Particle outputParticle,
		AtomMetadata metadata
	);


	/**
	 * @return true, if the output is accounted for, false, otherwise
	 */
	boolean outputExecute(Particle output, AtomMetadata metadata);
}
