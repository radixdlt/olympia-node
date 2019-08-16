package com.radixdlt.atommodel.procedures;

import com.radixdlt.atomos.Result;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.function.BiFunction;

/**
 * Transition definition from one particle to two particles
 */
public final class CombinedTransition<T extends Particle, U extends Particle, V extends Particle>
	implements TransitionProcedure<T, U> {
	private final Class<V> particleClass0;
	private final BiFunction<U, V, Result> combinedCheck;

	public CombinedTransition(
		Class<V> particleClass0,
		BiFunction<U, V, Result> combinedCheck
	) {
		this.particleClass0 = particleClass0;
		this.combinedCheck = combinedCheck;
	}

	@Override
	public ProcedureResult execute(
		T inputParticle,
		Object inputUsed,
		U outputParticle,
		Object outputUsed
	) {
		if (inputUsed == null) {
			return ProcedureResult.popOutput(outputParticle);
		}

		if (!(inputUsed.getClass().equals(particleClass0))) {
			return ProcedureResult.error("Expecting a previous input used class of " + particleClass0);
		}

		Result combinedCheckResult = combinedCheck.apply(outputParticle, particleClass0.cast(inputUsed));
		if (combinedCheckResult.isError()) {
			return ProcedureResult.error(combinedCheckResult.getErrorMessage());
		}

		return ProcedureResult.popInputOutput();
	}
}
