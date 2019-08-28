package com.radixdlt.atomos;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedData;

public interface RoutineCalls {
	/**
	 * Defines a valid transition in the constraint machine as well as the
	 * requirements for executing that transition.
	 *
	 * @param <I> input particle type
	 * @param <U> output particle type
	 */
	<I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> void createTransition(
		TransitionToken<I, N, O, U> transitionToken,
		TransitionProcedure<I, N, O, U> transitionProcedure
	);
}
