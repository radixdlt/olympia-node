/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UIntUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Transition Procedure for one to one fungible types
 */
public class CreateFungibleTransitionRoutine<I extends Particle, O extends Particle> implements ConstraintRoutine {
	public static final class UsedAmount implements UsedData {
		private final UInt256 amount;

		public UsedAmount(UInt256 usedAmount) {
			this.amount = Objects.requireNonNull(usedAmount);
		}

		public UInt256 getUsedAmount() {
			return this.amount;
		}

		@Override
		public TypeToken<? extends UsedData> getTypeToken() {
			return TypeToken.of(UsedAmount.class);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(amount);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof UsedAmount)) {
				return false;
			}

			UsedAmount u = (UsedAmount) obj;
			return Objects.equals(this.amount, u.amount);
		}

		@Override
		public String toString() {
			return String.valueOf(this.amount);
		}
	}

	private final Class<I> inputClass;
	private final Class<O> outputClass;
	private final Function<I, UInt256> inputAmountMapper;
	private final Function<O, UInt256> outputAmountMapper;
	private final BiFunction<I, O, Result> transition;
	private final SignatureValidator<I> inputSignatureValidator;

	public CreateFungibleTransitionRoutine(
		Class<I> inputClass,
		Class<O> outputClass,
		Function<I, UInt256> inputAmountMapper,
		Function<O, UInt256> outputAmountMapper,
		BiFunction<I, O, Result> transition,
		SignatureValidator<I> inputSignatureValidator
	) {
		Objects.requireNonNull(inputAmountMapper);
		Objects.requireNonNull(outputAmountMapper);
		Objects.requireNonNull(transition);

		this.inputClass = inputClass;
		this.outputClass = outputClass;
		this.inputAmountMapper = inputAmountMapper;
		this.outputAmountMapper = outputAmountMapper;
		this.transition = transition;
		this.inputSignatureValidator = inputSignatureValidator;
	}

	@Override
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

	protected class FungibleTransitionProcedure<N extends UsedData, U extends UsedData> implements TransitionProcedure<I, N, O, U> {
		private final Function<N, UInt256> inputUsedMapper;
		private final Function<U, UInt256> outputUsedMapper;
		private final UsedCompute<I, N, O, U> additionalOutputUsedCompute;

		public FungibleTransitionProcedure(
			Function<N, UInt256> inputUsedMapper,
			Function<U, UInt256> outputUsedMapper
		) {
			this(inputUsedMapper,
				outputUsedMapper,
				(ip, iu, op, ou) -> Optional.empty()
			);
		}

		public FungibleTransitionProcedure(
			Function<N, UInt256> inputUsedMapper,
			Function<U, UInt256> outputUsedMapper,
			UsedCompute<I, N, O, U> additionalOutputUsedCompute
		) {
			this.inputUsedMapper = inputUsedMapper;
			this.outputUsedMapper = outputUsedMapper;
			this.additionalOutputUsedCompute = additionalOutputUsedCompute;
		}

		@Override
		public Result precondition(I inputParticle, N inputUsed, O outputParticle, U outputUsed) {
			return transition.apply(inputParticle, outputParticle);
		}

		@Override
		public UsedCompute<I, N, O, U> inputUsedCompute() {
			return (inputParticle, inputUsed, outputParticle, outputUsed) -> {
				final UInt256 inputUsedAmount = inputUsedMapper.apply(inputUsed);
				final UInt256 inputAmount =
					UIntUtils.subtractWithUnderflow(inputAmountMapper.apply(inputParticle), inputUsedAmount);
				final UInt256 outputAmount =
					UIntUtils.subtractWithUnderflow(outputAmountMapper.apply(outputParticle), outputUsedMapper.apply(outputUsed));
				// Note that overflow is not possible in the addition below.
				// Given
				//   inputAmount > outputAmount                                  (comparison in java code below)
				//   => inputParticle - inputUsed > outputParticle - outputUsed  (substitute equalities)
				//   => outputParticle - outputUsed < inputParticle - inputUsed  (rearrange [1])
				// and
				//   inputUsed + outputAmount <= MAX_VALUE                       (otherwise overflow occurs)
				//   => inputUsed + outputParticle - outputUsed <= MAX_VALUE     (substitute equalities [2])
				//
				// Assume that
				//   inputUsed + outputParticle - outputUsed > MAX_VALUE         (contradiction of [2])
				// but
				//   outputParticle - outputUsed < inputParticle - inputUsed     (from [1])
				// so this also must be true
				//   inputUsed + inputParticle - inputUsed > MAX_VALUE           (substitute larger term)
				//   => inputParticle > MAX_VALUE                                (combine terms)
				// but this cannot be true, due to properties of variables, therefore
				//   inputUsed + outputParticle - outputUsed <= MAX_VALUE
				int compare = inputAmount.compareTo(outputAmount);
				return compare > 0
					? Optional.of(new UsedAmount(inputUsedAmount.add(outputAmount)))
					: Optional.empty();
			};
		}

		@Override
		public UsedCompute<I, N, O, U> outputUsedCompute() {
			return (inputParticle, inputUsed, outputParticle, outputUsed) -> {
				final UInt256 outputUsedAmount = outputUsedMapper.apply(outputUsed);
				final UInt256 inputAmount =
					UIntUtils.subtractWithUnderflow(inputAmountMapper.apply(inputParticle), inputUsedMapper.apply(inputUsed));
				final UInt256 outputAmount =
					UIntUtils.subtractWithUnderflow(outputAmountMapper.apply(outputParticle), outputUsedAmount);
				// Note that overflow is not possible in the addition below.
				// Given
				//   inputAmount < outputAmount                                  (comparison in java code below)
				//   => inputParticle - inputUsed < outputParticle - outputUsed  (substitute equalities [1])
				//   => outputParticle - outputUsed > inputParticle - inputUsed  (rearrange [1])
				// and
				//   outputUsed + inputAmount <= MAX_VALUE                       (otherwise overflow occurs)
				//   => outputUsed + inputParticle - inputUsed <= MAX_VALUE      (substitute equalities [2])
				//
				// Assume that
				//   outputUsed + inputParticle - inputUsed > MAX_VALUE          (contradiction of [2])
				// but
				//   outputParticle - outputUsed > inputParticle - inputUsed     (from [1])
				// so this also must be true
				//   outputUsed + outputParticle - outputUsed > MAX_VALUE        (substitute larger term)
				//   => outputParticle > MAX_VALUE                               (combine terms)
				// but this cannot be true, due to properties of variables, therefore
				//   outputUsed + inputParticle - inputUsed <= MAX_VALUE
				int compare = inputAmount.compareTo(outputAmount);
				return compare < 0
					? Optional.of(new UsedAmount(UIntUtils.addWithOverflow(outputUsedAmount, inputAmount)))
					: this.additionalOutputUsedCompute.compute(inputParticle, inputUsed, outputParticle, outputUsed);
			};
		}

		@Override
		public SignatureValidator<I> inputSignatureRequired() {
			return inputSignatureValidator;
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

	public Class<I> getInputClass() {
		return inputClass;
	}

	public Class<O> getOutputClass() {
		return outputClass;
	}

	public SignatureValidator<I> getInputWitnessValidator() {
		return inputSignatureValidator;
	}
}