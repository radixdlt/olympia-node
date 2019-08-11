package com.radixdlt.atommodel.procedures;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.atomos.WitnessValidator;
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
	private final WitnessValidator<T> witnessValidator;

	public FungibleTransition(
		Function<T, UInt256> inputAmountMapper,
		Function<U, UInt256> outputAmountMapper,
		BiPredicate<T, U> transition,
		WitnessValidator<T> witnessValidator
	) {
		this.inputAmountMapper = inputAmountMapper;
		this.outputAmountMapper = outputAmountMapper;
		this.transition = transition;
		this.witnessValidator = witnessValidator;
	}

	@Override
	public ProcedureResult execute(
		T inputParticle,
		AtomicReference<Object> inputData,
		U outputParticle,
		AtomicReference<Object> outputData
	) {
		if (!transition.test(inputParticle, outputParticle)) {
			return ProcedureResult.ERROR;
		}

		UInt256 inputAmount = inputData.get() == null
			? inputAmountMapper.apply((T) inputParticle)
			: (UInt256) inputData.get();
		UInt256 outputAmount = outputData.get() == null
			? outputAmountMapper.apply((U) outputParticle)
			: (UInt256) outputData.get();

		int compare = inputAmount.compareTo(outputAmount);
		if (compare == 0) {
			return ProcedureResult.POP_INPUT_OUTPUT;
		} else if (compare > 0) {
			inputData.set(inputAmount.subtract(outputAmount));
			return ProcedureResult.POP_OUTPUT;
		} else {
			outputData.set(outputAmount.subtract(inputAmount));
			return ProcedureResult.POP_INPUT;
		}
	}

	@Override
	public boolean validateWitness(
		ProcedureResult result,
		T inputParticle,
		U outputParticle,
		AtomMetadata metadata
	) {
		switch (result) {
			case POP_OUTPUT:
				return true;
			case POP_INPUT:
			case POP_INPUT_OUTPUT:
				return witnessValidator.validate(inputParticle, metadata).isSuccess();
			default:
				throw new IllegalStateException();
		}
	}
}