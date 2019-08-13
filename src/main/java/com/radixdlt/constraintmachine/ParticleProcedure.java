package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Stack;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 * TODO: split transition checks and witness validator
 */
public interface ParticleProcedure {

	/**
	 * Given a runtime of a stack of outputs, decides how an input particle will affect the
	 * runtime environment.
	 * @returns true, if the input has been used up, false, otherwise
	 */
	boolean inputExecute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs);

	/**
	 * @return true, if the output is accounted for, false, otherwise
	 */
	boolean outputExecute(Particle output, AtomMetadata metadata);
}
