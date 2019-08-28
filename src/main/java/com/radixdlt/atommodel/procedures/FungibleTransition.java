package com.radixdlt.atommodel.procedures;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Transition Procedure for one to one fungible types
 */
public final class FungibleTransition<T extends Particle, U extends Particle> {
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

	private static ProcedureResult calculate(UInt256 in, UInt256 out) {
		int compare = in.compareTo(out);
		if (compare == 0) {
			return ProcedureResult.popInputOutput();
		} else if (compare > 0) {
			return ProcedureResult.popOutput(new UsedAmount(out));
		} else {
			return ProcedureResult.popInput(new UsedAmount(in));
		}
	}

	public TransitionProcedure<T, VoidUsedData, U, VoidUsedData> getProcedure0() {
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

	public TransitionProcedure<T, UsedAmount, U, VoidUsedData> getProcedure1() {
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

	public TransitionProcedure<T, VoidUsedData, U, UsedAmount> getProcedure2() {
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