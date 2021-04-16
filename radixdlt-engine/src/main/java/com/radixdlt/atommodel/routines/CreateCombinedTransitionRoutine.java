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
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.store.ImmutableIndex;

import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Transition procedure for a transition from one particle type to two particle types.
 */
public final class CreateCombinedTransitionRoutine<I extends Particle, O extends Particle, V extends Particle> implements ConstraintRoutine {

	public static class UsedParticle<P extends Particle> implements ReducerState {
		private final P usedParticle;
		private final TypeToken<UsedParticle<P>> typeToken;
		UsedParticle(TypeToken<UsedParticle<P>> typeToken, P usedParticle) {
			this.typeToken = typeToken;
			this.usedParticle = usedParticle;
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return typeToken;
		}
	}
	private final Class<I> inputClass;
	private final Class<O> outputClass0;
	private final BiFunction<I, O, PermissionLevel> permissionLevel;
	private final Class<V> outputClass1;
	private final BiFunction<O, V, Result> combinedCheck;
	private final TypeToken<UsedParticle<O>> typeToken0;
	private final SignatureValidator<I> inputSignatureValidator;
	private final Predicate<O> includeSecondClass;

	public CreateCombinedTransitionRoutine(
		Class<I> inputClass,
		Class<O> outputClass0,
		BiFunction<I, O, PermissionLevel> permissionLevel,
		Class<V> outputClass1,
		Predicate<O> includeSecondClass,
		BiFunction<O, V, Result> combinedCheck,
		SignatureValidator<I> inputSignatureValidator
	) {
		this.inputClass = inputClass;
		this.outputClass0 = outputClass0;
		this.permissionLevel = permissionLevel;
		this.outputClass1 = outputClass1;
		this.includeSecondClass = includeSecondClass;

		this.typeToken0 = new TypeToken<UsedParticle<O>>() { }.where(new TypeParameter<O>() { }, outputClass0);
		this.combinedCheck = combinedCheck;
		this.inputSignatureValidator = inputSignatureValidator;
	}

	@Override
	public void main(RoutineCalls calls) {
		calls.createTransition(
			new TransitionToken<>(inputClass, outputClass0, TypeToken.of(VoidReducerState.class)),
			getProcedure0()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, outputClass1, typeToken0),
			getProcedure2()
		);
	}

	public TransitionProcedure<I, O, VoidReducerState> getProcedure0() {
		return new TransitionProcedure<I, O, VoidReducerState>() {
			@Override
			public PermissionLevel requiredPermissionLevel(I inputParticle, O outputParticle) {
				return permissionLevel.apply(inputParticle, outputParticle);
			}

			@Override
			public Result precondition(I inputParticle, O outputParticle, VoidReducerState outputUsed, ImmutableIndex index) {
				return Result.success();
			}

			@Override
			public InputOutputReducer<I, O, VoidReducerState> inputOutputReducer() {
				return (input, output, index, outputUsed) ->
					includeSecondClass.test(output)
						? ReducerResult.incomplete(new UsedParticle<>(typeToken0, output), true)
						: ReducerResult.complete(Unknown.create());
			}

			@Override
			public SignatureValidator<I> inputSignatureRequired() {
				return inputSignatureValidator;
			}
		};
	}

	public TransitionProcedure<I, V, UsedParticle<O>> getProcedure2() {
		return new TransitionProcedure<I, V, UsedParticle<O>>() {
			@Override
			public Result precondition(I inputParticle, V outputParticle, UsedParticle<O> inputUsed, ImmutableIndex index) {
				return combinedCheck.apply(inputUsed.usedParticle, outputParticle);
			}

			@Override
			public InputOutputReducer<I, V, UsedParticle<O>> inputOutputReducer() {
				return (input, output, index, outputUsed) -> ReducerResult.complete(Unknown.create());
			}

			@Override
			public SignatureValidator<I> inputSignatureRequired() {
				return inputSignatureValidator;
			}
		};
	}
}
