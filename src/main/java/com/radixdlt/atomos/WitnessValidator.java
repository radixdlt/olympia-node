package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;

public interface WitnessValidator<T extends Particle, U extends Particle> {
	boolean validate(
		ProcedureResult result,
		T inputParticle,
		U outputParticle,
		AtomMetadata metadata
	);
}
