package com.radixdlt.atomos.procedures;

import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Stack;

public interface ParticleProcedure {
	boolean inputExecute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs);
	boolean outputExecute(Particle output, AtomMetadata metadata);
}
