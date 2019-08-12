package com.radixdlt.atommodel.procedures;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.utils.UInt256;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Low-level implementation of fungible transition constraints.
 */
public class FungibleTransition<T extends Particle, U extends Particle> implements TransitionProcedure<T, U> {
	private final Function<T, UInt256> inputAmountMapper;
	private final Function<U, UInt256> outputAmountMapper;
	private final BiPredicate<T, U> transition;

	public FungibleTransition(
		Function<T, UInt256> inputAmountMapper,
		Function<U, UInt256> outputAmountMapper,
		BiPredicate<T, U> transition
	) {
		this.inputAmountMapper = inputAmountMapper;
		this.outputAmountMapper = outputAmountMapper;
		this.transition = transition;
	}

	@Override
	public ProcedureResult execute(
		T inputParticle,
		AtomicReference<Object> inputData,
		U outputParticle,
		AtomicReference<Object> outputData
	) {
		if (!transition.test(inputParticle, outputParticle)) {
			return new ProcedureResult(CMAction.ERROR);
		}

		UInt256 inputAmount = inputData.get() == null
			? inputAmountMapper.apply(inputParticle)
			: (UInt256) inputData.get();
		UInt256 outputAmount = outputData.get() == null
			? outputAmountMapper.apply(outputParticle)
			: (UInt256) outputData.get();

		int compare = inputAmount.compareTo(outputAmount);
		if (compare == 0) {
			return new ProcedureResult(CMAction.POP_INPUT_OUTPUT);
		} else if (compare > 0) {
			inputData.set(inputAmount.subtract(outputAmount));
			return new ProcedureResult(CMAction.POP_OUTPUT);
		} else {
			outputData.set(outputAmount.subtract(inputAmount));
			return new ProcedureResult(CMAction.POP_INPUT);
		}
	}
}