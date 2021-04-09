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

import com.google.common.reflect.TypeParameter;
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
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Transition procedure for a transition from one particle type to two particle types.
 */
public final class CreateCombinedTransitionRoutine<I extends Particle, O extends Particle, V extends Particle> implements ConstraintRoutine {

	public static class UsedParticle<P extends Particle> implements UsedData {
		private final P usedParticle;
		private final TypeToken<UsedParticle<P>> typeToken;
		UsedParticle(TypeToken<UsedParticle<P>> typeToken, P usedParticle) {
			this.typeToken = typeToken;
			this.usedParticle = usedParticle;
		}

		@Override
		public TypeToken<? extends UsedData> getTypeToken() {
			return typeToken;
		}
	}
	private final Class<I> inputClass;
	private final Class<O> outputClass0;
	private final Class<V> outputClass1;
	private final BiFunction<O, V, Result> combinedCheck;
	private final TypeToken<UsedParticle<O>> typeToken0;
	private final TypeToken<UsedParticle<V>> typeToken1;
	private final SignatureValidator<I> inputSignatureValidator;

	public CreateCombinedTransitionRoutine(
		Class<I> inputClass,
		Class<O> outputClass0,
		Class<V> outputClass1,
		BiFunction<O, V, Result> combinedCheck,
		SignatureValidator<I> inputSignatureValidator
	) {
		this.inputClass = inputClass;
		this.outputClass0 = outputClass0;
		this.outputClass1 = outputClass1;

		this.typeToken0 = new TypeToken<UsedParticle<O>>() { }.where(new TypeParameter<O>() { }, outputClass0);
		this.typeToken1 = new TypeToken<UsedParticle<V>>() { }.where(new TypeParameter<V>() { }, outputClass1);
		this.combinedCheck = combinedCheck;
		this.inputSignatureValidator = inputSignatureValidator;
	}

	@Override
	public void main(RoutineCalls calls) {
		calls.createTransition(
			new TransitionToken<>(inputClass, TypeToken.of(VoidUsedData.class), outputClass0, TypeToken.of(VoidUsedData.class)),
			getProcedure0()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, typeToken1, outputClass0, TypeToken.of(VoidUsedData.class)),
			getProcedure2()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, TypeToken.of(VoidUsedData.class), outputClass1, TypeToken.of(VoidUsedData.class)),
			getProcedure1()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, typeToken0, outputClass1, TypeToken.of(VoidUsedData.class)),
			getProcedure3()
		);
	}

	public TransitionProcedure<I, VoidUsedData, O, VoidUsedData> getProcedure0() {
		return new TransitionProcedure<I, VoidUsedData, O, VoidUsedData>() {
			@Override
			public Result precondition(I inputParticle, VoidUsedData inputUsed, O outputParticle, VoidUsedData outputUsed) {
				return Result.success();
			}

			@Override
			public UsedCompute<I, VoidUsedData, O, VoidUsedData> inputUsedCompute() {
				return (input, inputUsed, output, outputUsed) -> Optional.of(new UsedParticle<>(typeToken0, output));
			}

			@Override
			public SignatureValidator<I> inputSignatureRequired() {
				throw new IllegalStateException("Should never call here");
			}

			@Override
			public UsedCompute<I, VoidUsedData, O, VoidUsedData> outputUsedCompute() {
				return (input, inputUsed, output, outputUsed) -> Optional.empty();
			}
		};
	}

	public TransitionProcedure<I, VoidUsedData, V, VoidUsedData> getProcedure1() {
		return new TransitionProcedure<I, VoidUsedData, V, VoidUsedData>() {
			@Override
			public Result precondition(I inputParticle, VoidUsedData inputUsed, V outputParticle, VoidUsedData outputUsed) {
				return Result.success();
			}

			@Override
			public UsedCompute<I, VoidUsedData, V, VoidUsedData> inputUsedCompute() {
				return (input, inputUsed, output, outputUsed) -> Optional.of(new UsedParticle<>(typeToken1, output));
			}

			@Override
			public UsedCompute<I, VoidUsedData, V, VoidUsedData> outputUsedCompute() {
				return (input, inputUsed, output, outputUsed) -> Optional.empty();
			}

			@Override
			public SignatureValidator<I> inputSignatureRequired() {
				throw new IllegalStateException("Should never call here");
			}

		};
	}

	public TransitionProcedure<I, UsedParticle<V>, O, VoidUsedData> getProcedure2() {
		return new TransitionProcedure<I, UsedParticle<V>, O, VoidUsedData>() {
			@Override
			public Result precondition(I inputParticle, UsedParticle<V> inputUsed, O outputParticle, VoidUsedData outputUsed) {
				return combinedCheck.apply(outputParticle, inputUsed.usedParticle);
			}

			@Override
			public UsedCompute<I, UsedParticle<V>, O, VoidUsedData> inputUsedCompute() {
				return (input, inputUsed, output, outputUsed) -> Optional.empty();
			}

			@Override
			public UsedCompute<I, UsedParticle<V>, O, VoidUsedData> outputUsedCompute() {
				return (input, inputUsed, output, outputUsed) -> Optional.empty();
			}

			@Override
			public SignatureValidator<I> inputSignatureRequired() {
				return inputSignatureValidator;
			}
		};
	}

	public TransitionProcedure<I, UsedParticle<O>, V, VoidUsedData> getProcedure3() {
		return new TransitionProcedure<I, UsedParticle<O>, V, VoidUsedData>() {
			@Override
			public Result precondition(I inputParticle, UsedParticle<O> inputUsed, V outputParticle, VoidUsedData outputUsed) {
				return combinedCheck.apply(inputUsed.usedParticle, outputParticle);
			}

			@Override
			public UsedCompute<I, UsedParticle<O>, V, VoidUsedData> inputUsedCompute() {
				return (input, inputUsed, output, outputUsed) -> Optional.empty();
			}

			@Override
			public UsedCompute<I, UsedParticle<O>, V, VoidUsedData> outputUsedCompute() {
				return (input, inputUsed, output, outputUsed) -> Optional.empty();
			}

			@Override
			public SignatureValidator<I> inputSignatureRequired() {
				return inputSignatureValidator;
			}
		};
	}
}
