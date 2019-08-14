package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Transition definition from RRI to the second of two combined particles
 */
public final class RRIResourceCombinedSecondaryCreation<T extends Particle, U extends Particle> implements TransitionProcedure<RRIParticle, U> {
	private final Class<T> particleClass0;
	private final Function<U, RRI> rriMapper1;
	private final BiPredicate<T, U> combinedCheck;

	RRIResourceCombinedSecondaryCreation(
		Class<T> particleClass0,
		Function<U, RRI> rriMapper1,
		BiPredicate<T, U> combinedCheck
	) {
		this.particleClass0 = particleClass0;
		this.rriMapper1 = rriMapper1;
		this.combinedCheck = combinedCheck;
	}

	@Override
	public ProcedureResult execute(
		RRIParticle inputParticle,
		U outputParticle,
		ProcedureResult prevResult
	) {
		if (prevResult == null) {
			return ProcedureResult.error();
		}

		Optional<T> input = prevResult.getInputUsed(Object.class)
			.filter(o -> o.getClass().equals(particleClass0))
			.map(particleClass0::cast);
		if (!input.isPresent()) {
			return ProcedureResult.error();
		}

		if (!combinedCheck.test(input.get(), outputParticle)) {
			return ProcedureResult.error();
		}

		if (!rriMapper1.apply(outputParticle).equals(inputParticle.getRri())) {
			return ProcedureResult.error();
		}

		return ProcedureResult.popInputOutput();
	}
}
