package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.procedures.ParticleProcedure;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Temporary helper class in transforming constraint to validator.
 * Should NOT be used by higher level abstractions.
 *
 * TODO: remove at some point after refactor of constraint machine
 */
public interface ConstraintProcedure {
	Map<Class<? extends Particle>, ParticleProcedure> getProcedures();
	Stream<ProcedureError> validate(ParticleGroup particleGroup, AtomMetadata metadata);
}
