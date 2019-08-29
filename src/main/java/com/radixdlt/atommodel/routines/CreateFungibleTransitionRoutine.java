package com.radixdlt.atommodel.routines;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedCompute;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Transition Procedure for one to one fungible types
 */
public final class CreateFungibleTransitionRoutine<I extends Particle, O extends Particle> implements ConstraintRoutine {
	public static class UsedAmount implements UsedData {
		private final UInt256 usedAmount;
		UsedAmount(UInt256 usedAmount) {
			this.usedAmount = usedAmount;
		}

		public UInt256 getUsedAmount() {
			return usedAmount;
		}

		@Override
		public TypeToken<? extends UsedData> getTypeToken() {
			return TypeToken.of(UsedAmount.class);
		}

		@Override
		public int hashCode() {
			return usedAmount.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof UsedAmount)) {
				return false;
			}

			UsedAmount u = (UsedAmount) obj;
			return this.usedAmount.equals(u.usedAmount);
		}
	}

	private final Class<I> inputClass;
	private final Class<O> outputClass;
	private final Function<I, UInt256> inputAmountMapper;
	private final Function<O, UInt256> outputAmountMapper;
	private final BiFunction<I, O, Result> transition;
	private final WitnessValidator<I> inputWitnessValidator;

	public CreateFungibleTransitionRoutine(
		Class<I> inputClass,
		Class<O> outputClass,
		Function<I, UInt256> inputAmountMapper,
		Function<O, UInt256> outputAmountMapper,
		BiFunction<I, O, Result> transition,
		WitnessValidator<I> inputWitnessValidator
	) {
		Objects.requireNonNull(inputAmountMapper);
		Objects.requireNonNull(outputAmountMapper);
		Objects.requireNonNull(transition);

		this.inputClass = inputClass;
		this.outputClass = outputClass;
		this.inputAmountMapper = inputAmountMapper;
		this.outputAmountMapper = outputAmountMapper;
		this.transition = transition;
		this.inputWitnessValidator = inputWitnessValidator;
	}

	public void main(RoutineCalls calls) {
		calls.createTransition(
			new TransitionToken<>(inputClass, TypeToken.of(VoidUsedData.class), outputClass, TypeToken.of(VoidUsedData.class)),
			getProcedure0()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, TypeToken.of(UsedAmount.class), outputClass, TypeToken.of(VoidUsedData.class)),
			getProcedure1()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, TypeToken.of(VoidUsedData.class), outputClass, TypeToken.of(UsedAmount.class)),
			getProcedure2()
		);
	}

	private class FungibleTransitionProcedure<N extends UsedData, U extends UsedData> implements TransitionProcedure<I, N, O, U> {
		private final Function<N, UInt256> inputUsedMapper;
		private final Function<U, UInt256> outputUsedMapper;

		private FungibleTransitionProcedure(
			Function<N, UInt256> inputUsedMapper,
			Function<U, UInt256> outputUsedMapper
		) {
			this.inputUsedMapper = inputUsedMapper;
			this.outputUsedMapper = outputUsedMapper;
		}

		@Override
		public Result precondition(I inputParticle, N inputUsed, O outputParticle, U outputUsed) {
			return transition.apply(inputParticle, outputParticle);
		}

		@Override
		public UsedCompute<I, N, O, U> inputUsedCompute() {
			return (inputParticle, inputUsed, outputParticle, outputUsed) -> {
				final UInt256 inputUsedAmount = inputUsedMapper.apply(inputUsed);
				final UInt256 inputAmount = inputAmountMapper.apply(inputParticle).subtract(inputUsedAmount);
				final UInt256 outputAmount = outputAmountMapper.apply(outputParticle).subtract(outputUsedMapper.apply(outputUsed));
				int compare = inputAmount.compareTo(outputAmount);
				return compare > 0 ? Optional.of(new UsedAmount(inputUsedAmount.add(outputAmount))) : Optional.empty();
			};
		}

		@Override
		public UsedCompute<I, N, O, U> outputUsedCompute() {
			return (inputParticle, inputUsed, outputParticle, outputUsed) -> {
				final UInt256 outputUsedAmount = outputUsedMapper.apply(outputUsed);
				final UInt256 inputAmount = inputAmountMapper.apply(inputParticle).subtract(inputUsedMapper.apply(inputUsed));
				final UInt256 outputAmount = outputAmountMapper.apply(outputParticle).subtract(outputUsedAmount);
				int compare = inputAmount.compareTo(outputAmount);
				return compare < 0 ? Optional.of(new UsedAmount(outputUsedAmount.add(inputAmount))) : Optional.empty();
			};
		}

		@Override
		public WitnessValidator<I> inputWitnessValidator() {
			return inputWitnessValidator;
		}

		@Override
		public WitnessValidator<O> outputWitnessValidator() {
			return (p, witnessData) -> WitnessValidatorResult.success();
		}
	}

	public TransitionProcedure<I, VoidUsedData, O, VoidUsedData> getProcedure0() {
		return new FungibleTransitionProcedure<>(u -> UInt256.ZERO, u -> UInt256.ZERO);
	}

	public TransitionProcedure<I, UsedAmount, O, VoidUsedData> getProcedure1() {
		return new FungibleTransitionProcedure<>(UsedAmount::getUsedAmount, u -> UInt256.ZERO);
	}

	public TransitionProcedure<I, VoidUsedData, O, UsedAmount> getProcedure2() {
		return new FungibleTransitionProcedure<>(u -> UInt256.ZERO, UsedAmount::getUsedAmount);
	}
}