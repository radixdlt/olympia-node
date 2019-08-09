package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Stack;

/**
 * TODO: split transition checks and witness validator
 */
public interface ParticleProcedure {
	boolean inputExecute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs);
	boolean outputExecute(Particle output, AtomMetadata metadata);
}
