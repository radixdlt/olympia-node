package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 * TODO: split transition checks and witness validator
 */
public interface TransitionProcedure {
	enum ProcedureResult {
		POP_INPUT,
		POP_OUTPUT,
		POP_INPUT_OUTPUT,
		ERROR
	}

	ImmutableSet<Pair<Class<? extends Particle>, Class<? extends Particle>>> supports();

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
}
