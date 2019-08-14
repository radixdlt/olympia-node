package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure.CMAction;
import java.util.Optional;

/**
 * Validates whether a specific transition procedure is permissible
 * @param <T> input particle class
 * @param <U> output particle class
 */
public interface WitnessValidator<T extends Particle, U extends Particle> {
	Optional<String> validate(
		CMAction result,
		T inputParticle,
		U outputParticle,
		AtomMetadata metadata
	);
}
