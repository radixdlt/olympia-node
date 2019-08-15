package com.radixdlt.atommodel.procedures;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Low-level implementation of fungible transition constraints.
 */
public final class FungibleTransition<T extends Particle, U extends Particle> implements TransitionProcedure<T, U> {
	private final Function<T, UInt256> inputAmountMapper;
	private final Function<U, UInt256> outputAmountMapper;
	private final BiFunction<T, U, Optional<String>> transition;

	public FungibleTransition(
		Function<T, UInt256> inputAmountMapper,
		Function<U, UInt256> outputAmountMapper,
		BiFunction<T, U, Optional<String>> transition
	) {
		Objects.requireNonNull(inputAmountMapper);
		Objects.requireNonNull(outputAmountMapper);
		Objects.requireNonNull(transition);

		this.inputAmountMapper = inputAmountMapper;
		this.outputAmountMapper = outputAmountMapper;
		this.transition = transition;
	}

	@Override
	public ProcedureResult execute(
		T inputParticle,
		Object inputUsed,
		U outputParticle,
		Object outputUsed
	) {
		final Optional<String> transitionErrorMessage = transition.apply(inputParticle, outputParticle);
		if (transitionErrorMessage.isPresent()) {
			return ProcedureResult.error(transitionErrorMessage.get());
		}

		UInt256 inputAmount = inputAmountMapper.apply(inputParticle).subtract(
			inputUsed != null ? (UInt256) inputUsed : UInt256.ZERO
		);

		UInt256 outputAmount = outputAmountMapper.apply(outputParticle).subtract(
			outputUsed != null ? (UInt256) outputUsed : UInt256.ZERO
		);

		int compare = inputAmount.compareTo(outputAmount);
		if (compare == 0) {
			return ProcedureResult.popInputOutput();
		} else if (compare > 0) {
			return ProcedureResult.popOutput(outputAmount);
		} else {
			return ProcedureResult.popInput(inputAmount);
		}
	}
}