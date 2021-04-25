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
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.ConstraintScryptEnv;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.SubstateWithArg;
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
	private final BiFunction<SubstateWithArg<I>, O, PermissionLevel> permissionLevel;
	private final Class<V> outputClass1;
	private final BiFunction<O, V, Result> combinedCheck;
	private final TypeToken<UsedParticle<O>> typeToken0;
	private final SignatureValidator<I, O> signatureValidator;
	private final Predicate<O> includeSecondClass;

	public CreateCombinedTransitionRoutine(
		Class<I> inputClass,
		Class<O> outputClass0,
		BiFunction<SubstateWithArg<I>, O, PermissionLevel> permissionLevel,
		Class<V> outputClass1,
		Predicate<O> includeSecondClass,
		BiFunction<O, V, Result> combinedCheck,
		SignatureValidator<I, O> signatureValidator
	) {
		this.inputClass = inputClass;
		this.outputClass0 = outputClass0;
		this.permissionLevel = permissionLevel;
		this.outputClass1 = outputClass1;
		this.includeSecondClass = includeSecondClass;

		this.typeToken0 = new TypeToken<UsedParticle<O>>() { }.where(new TypeParameter<O>() { }, outputClass0);
		this.combinedCheck = combinedCheck;
		this.signatureValidator = signatureValidator;
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
			public PermissionLevel requiredPermissionLevel(SubstateWithArg<I> in, O outputParticle, ImmutableIndex index) {
				return permissionLevel.apply(in, outputParticle);
			}

			@Override
			public Result precondition(SubstateWithArg<I> in, O outputParticle, VoidReducerState outputUsed, ImmutableIndex index) {
				// FIXME: HACK as we are assuming that this is a mutable token creation which is fine for
				// FIXME: now as it is the only available transition for betanet
				var argMaybe = in.getArg();
				if (argMaybe.isEmpty()) {
					return Result.error("Rri must be created with a name");
				}
				var arg = argMaybe.get();
				if (!ConstraintScryptEnv.NAME_PATTERN.matcher(new String(arg)).matches()) {
					return Result.error("invalid rri name");
				}
				return Result.success();
			}

			@Override
			public InputOutputReducer<I, O, VoidReducerState> inputOutputReducer() {
				return (input, output, index, outputUsed) -> {
					if (includeSecondClass.test(output)) {
						return ReducerResult.incomplete(new UsedParticle<>(typeToken0, output), true);
					} else {
						// FIXME: HACK as we are assuming that this is a mutable token creation which is fine for
						// FIXME: now as it is the only available transition for betanet
						var tokDefParticle = (TokenDefinitionParticle) output;
						var action = new CreateMutableToken(
							new String(input.getArg().orElseThrow()),
							tokDefParticle.getName(),
							tokDefParticle.getDescription(),
							tokDefParticle.getIconUrl(),
							tokDefParticle.getUrl()
						);
						return ReducerResult.complete(action);
					}
				};
			}

			@Override
			public SignatureValidator<I, O> signatureValidator() {
				return signatureValidator;
			}
		};
	}

	public TransitionProcedure<I, V, UsedParticle<O>> getProcedure2() {
		return new TransitionProcedure<I, V, UsedParticle<O>>() {
			@Override
			public Result precondition(
				SubstateWithArg<I> inputParticle,
				V outputParticle,
				UsedParticle<O> inputUsed,
				ImmutableIndex index
			) {
				return combinedCheck.apply(inputUsed.usedParticle, outputParticle);
			}

			@Override
			public InputOutputReducer<I, V, UsedParticle<O>> inputOutputReducer() {
				return (input, output, index, outputUsed) -> {
					// FIXME: HACK as we are assuming that this is a mutable token creation which is fine for
					// FIXME: now as it is the only available transition for betanet
					var tokDefParticle = (TokenDefinitionParticle) outputUsed.usedParticle;
					var tokens = (TokensParticle) output;
					var action = new CreateFixedToken(
						tokens.getResourceAddr(),
						tokens.getHoldingAddr(),
						new String(input.getArg().orElseThrow()),
						tokDefParticle.getName(),
						tokDefParticle.getDescription(),
						tokDefParticle.getIconUrl(),
						tokDefParticle.getUrl(),
						tokDefParticle.getSupply().orElseThrow()
					);
					return ReducerResult.complete(action);
				};
			}

			@Override
			public SignatureValidator<I, V> signatureValidator() {
				return (i, o, index, pubKey) -> true;
			}
		};
	}
}
