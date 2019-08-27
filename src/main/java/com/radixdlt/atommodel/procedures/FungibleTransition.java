package com.radixdlt.atommodel.procedures;

import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Transition Procedure for one to one fungible types
 */
public final class FungibleTransition<T extends Particle, U extends Particle> implements TransitionProcedure<T, U> {
	private final Function<T, UInt256> inputAmountMapper;
	private final Function<U, UInt256> outputAmountMapper;
	private final BiFunction<T, U, Result> transition;

	public FungibleTransition(
		Function<T, UInt256> inputAmountMapper,
		Function<U, UInt256> outputAmountMapper,
		BiFunction<T, U, Result> transition
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
		final Result transitionResult = transition.apply(inputParticle, outputParticle);
		if (transitionResult.isError()) {
			return ProcedureResult.error(transitionResult.getErrorMessage());
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