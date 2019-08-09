package com.radixdlt.atomos.procedures;

import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Stack;

public interface InputParticleProcedure {
	boolean execute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs);
}
