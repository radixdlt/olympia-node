package com.radixdlt.atommodel.routines;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
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

	private ProcedureResult<I, O> calculate(UInt256 in, UInt256 out) {
		int compare = in.compareTo(out);
		if (compare == 0) {
			return ProcedureResult.popInputOutput(
				inputWitnessValidator,
				(p, witnessData) -> WitnessValidatorResult.success()
			);
		} else if (compare > 0) {
			return ProcedureResult.popOutput(
				new UsedAmount(out),
				(p, witnessData) -> WitnessValidatorResult.success()
			);
		} else {
			return ProcedureResult.popInput(
				new UsedAmount(in),
				inputWitnessValidator
			);
		}
	}

	public TransitionProcedure<I, VoidUsedData, O, VoidUsedData> getProcedure0() {
		return (in, inUsed, out, outUsed) -> {
			final Result transitionResult = transition.apply(in, out);
			if (transitionResult.isError()) {
				return ProcedureResult.error(transitionResult.getErrorMessage());
			}

			final UInt256 inputAmount = inputAmountMapper.apply(in);
			final UInt256 outputAmount = outputAmountMapper.apply(out);
			return calculate(inputAmount, outputAmount);
		};
	}

	public TransitionProcedure<I, UsedAmount, O, VoidUsedData> getProcedure1() {
		return (in, inUsed, out, outUsed) -> {
			final Result transitionResult = transition.apply(in, out);
			if (transitionResult.isError()) {
				return ProcedureResult.error(transitionResult.getErrorMessage());
			}

			UInt256 inputAmount = inputAmountMapper.apply(in).subtract(inUsed.usedAmount);
			UInt256 outputAmount = outputAmountMapper.apply(out);
			return calculate(inputAmount, outputAmount);
		};
	}

	public TransitionProcedure<I, VoidUsedData, O, UsedAmount> getProcedure2() {
		return (in, inUsed, out, outUsed) -> {
			final Result transitionResult = transition.apply(in, out);
			if (transitionResult.isError()) {
				return ProcedureResult.error(transitionResult.getErrorMessage());
			}

			UInt256 inputAmount = inputAmountMapper.apply(in);
			UInt256 outputAmount = outputAmountMapper.apply(out).subtract(outUsed.usedAmount);
			return calculate(inputAmount, outputAmount);
		};
	}
}