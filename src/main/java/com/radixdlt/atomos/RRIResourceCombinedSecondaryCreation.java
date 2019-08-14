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
		U outputParticle,
		ProcedureResult prevResult
	) {
		if (prevResult == null) {
			return ProcedureResult.error("Expecting a previous result.");
		}

		Optional<T> input = prevResult.getInputUsed(Object.class)
			.filter(o -> o.getClass().equals(particleClass0))
			.map(particleClass0::cast);
		if (!input.isPresent()) {
			return ProcedureResult.error("Expecting a previous input used class of " + particleClass0);
		}

		Optional<String> combinedCheckError = combinedCheck.apply(input.get(), outputParticle);
		if (combinedCheckError.isPresent()) {
			return ProcedureResult.error(combinedCheckError.get());
		}

		return ProcedureResult.popInputOutput();
	}
}
