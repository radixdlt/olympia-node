package com.radixdlt.atommodel.procedures;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Low-level implementation of fungible transition constraints.
 */
public final class FungibleTransition<T extends Particle, U extends Particle> implements TransitionProcedure<T, U> {
	private final Function<T, UInt256> inputAmountMapper;
	private final Function<U, UInt256> outputAmountMapper;
	private final BiPredicate<T, U> transition;

	public FungibleTransition(
		Function<T, UInt256> inputAmountMapper,
		Function<U, UInt256> outputAmountMapper,
		BiPredicate<T, U> transition
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
		U outputParticle,
		ProcedureResult prevResult
	) {
		if (!transition.test(inputParticle, outputParticle)) {
			return ProcedureResult.error();
		}

		UInt256 inputAmount = Optional.ofNullable(prevResult)
			.flatMap(p -> p.getInputRemainder(UInt256.class))
			.orElseGet(() -> inputAmountMapper.apply(inputParticle));

		UInt256 outputAmount = Optional.ofNullable(prevResult)
			.flatMap(p -> p.getOutputRemainder(UInt256.class))
			.orElseGet(() -> outputAmountMapper.apply(outputParticle));


		int compare = inputAmount.compareTo(outputAmount);
		if (compare == 0) {
			return ProcedureResult.popInputOutput();
		} else if (compare > 0) {
			return ProcedureResult.popOutput(inputAmount.subtract(outputAmount));
		} else {
			return ProcedureResult.popInput(outputAmount.subtract(inputAmount));
		}
	}
}