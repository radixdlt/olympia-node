package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;
import java.util.Map;

/**
 * Temporary helper class in transforming constraint to validator.
 * Should NOT be used by higher level abstractions.
 *
 * TODO: remove at some point after refactor of constraint machine
 */
public interface ConstraintProcedure {
	Map<Class<? extends Particle>, ParticleProcedure> getProcedures();
}
