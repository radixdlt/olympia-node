package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Transition definition from RRI to the second of two combined particles
 */
public final class RRIResourceCombinedSecondaryCreation<T extends Particle, U extends Particle> implements TransitionProcedure<RRIParticle, U> {
	private final Class<T> particleClass0;
	private final BiFunction<T, U, Optional<String>> combinedCheck;

	RRIResourceCombinedSecondaryCreation(
		Class<T> particleClass0,
		BiFunction<T, U, Optional<String>> combinedCheck
	) {
		this.particleClass0 = particleClass0;
		this.combinedCheck = combinedCheck;
	}

	@Override
	public ProcedureResult execute(
		RRIParticle inputParticle,
		Object inputUsed,
		U outputParticle,
		Object outputUsed
	) {
		if (inputUsed == null) {
			return ProcedureResult.error("Expecting a previous result.");
		}

		if (!(inputUsed.getClass().equals(particleClass0))) {
			return ProcedureResult.error("Expecting a previous input used class of " + particleClass0);
		}

		Optional<String> combinedCheckError = combinedCheck.apply(particleClass0.cast(inputUsed), outputParticle);
		if (combinedCheckError.isPresent()) {
			return ProcedureResult.error(combinedCheckError.get());
		}

		return ProcedureResult.popInputOutput();
	}
}
