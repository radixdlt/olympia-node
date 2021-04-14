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

package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atommodel.routines.CreateCombinedTransitionRoutine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * SysCall environment for CMAtomOS Constraint Scrypts.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
final class ConstraintScryptEnv implements SysCalls {
	private final ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions;
	private final Function<RadixAddress, Result> addressChecker;

	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> scryptParticleDefinitions;
	private final Map<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> scryptTransitionProcedures;

	ConstraintScryptEnv(
		ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions,
		Function<RadixAddress, Result> addressChecker
	) {
		this.particleDefinitions = particleDefinitions;
		this.addressChecker = addressChecker;

		this.scryptParticleDefinitions = new HashMap<>();
		this.scryptTransitionProcedures = new HashMap<>();
	}

	public Map<Class<? extends Particle>, ParticleDefinition<Particle>> getScryptParticleDefinitions() {
		return scryptParticleDefinitions;
	}

	public Map<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> getScryptTransitionProcedures() {
		return scryptTransitionProcedures;
	}

	private <T extends Particle> boolean particleDefinitionExists(Class<T> particleClass) {
		return particleDefinitions.containsKey(particleClass) || scryptParticleDefinitions.containsKey(particleClass);
	}

	private <T extends Particle> ParticleDefinition<Particle> getParticleDefinition(Class<T> particleClass) {
		ParticleDefinition<Particle> particleDefinition = particleDefinitions.get(particleClass);
		if (particleDefinition != null) {
			if (!particleDefinition.allowsTransitionsFromOutsideScrypts()) {
				throw new IllegalStateException(particleClass + " can only be used in registering scrypt.");
			}
			return particleDefinition;
		}

		particleDefinition = scryptParticleDefinitions.get(particleClass);
		if (particleDefinition == null) {
			throw new IllegalStateException(particleClass + " is not registered.");
		}

		return particleDefinition;
	}


	@Override
	public <T extends Particle> void registerParticle(Class<T> particleClass, ParticleDefinition<T> particleDefinition) {
		if (particleDefinitionExists(particleClass)) {
			throw new IllegalStateException("Particle " + particleClass + " is already registered");
		}
		Objects.requireNonNull(particleDefinition, "particleDefinition");

		// TODO Cleanup: This redefinition illustrates that there's some abstraction issues here, but
		// TODO Cleanup: will leave for now since it's not critical and we anticipate a bigger refactor.
		ParticleDefinition.Builder<T> particleRedefinition = ParticleDefinition.<T>builder()
			.rriMapper(particleDefinition.getRriMapper())
			.virtualizeUp(particleDefinition.getVirtualizeSpin())
			.staticValidation(p -> {
				if (particleDefinition.getRriMapper() != null) {
					final RRI rri = particleDefinition.getRriMapper().apply(p);
					if (rri == null) {
						return Result.error("rri cannot be null");
					}

					final Result rriAddressResult = addressChecker.apply(rri.getAddress());
					if (rriAddressResult.isError()) {
						return rriAddressResult;
					}
				}

				return particleDefinition.getStaticValidation().apply(p);
			});
		if (particleDefinition.allowsTransitionsFromOutsideScrypts()) {
			particleRedefinition.allowTransitionsFromOutsideScrypts();
		}
		scryptParticleDefinitions.put(particleClass, particleRedefinition.build());
	}

	@Override
	public <O extends Particle> void createTransitionFromRRI(Class<O> particleClass) {
		ParticleDefinition<Particle> particleDefinition = getParticleDefinition(particleClass);
		if (particleDefinition.getRriMapper() == null) {
			throw new IllegalStateException(particleClass + " must be registered with an RRI mapper.");
		}

		createTransition(
			new TransitionToken<>(RRIParticle.class, particleClass, TypeToken.of(VoidReducerState.class)),
			new TransitionProcedure<>() {
				@Override
				public Result precondition(
					RRIParticle inputParticle,
					O outputParticle,
					VoidReducerState outputUsed
				) {
					return Result.success();
				}

				public InputOutputReducer<RRIParticle, O, VoidReducerState> inputOutputReducer() {
					return (input, output, index, outputUsed) -> ReducerResult.complete(Unknown.create());
				}

				@Override
				public SignatureValidator<RRIParticle> inputSignatureRequired() {
					return rri -> Optional.of(rri.getRri().getAddress());
				}
			}
		);
	}

	@Override
	public <O extends Particle, U extends Particle> void createTransitionFromRRICombined(
		Class<O> particleClass0,
		Class<U> particleClass1,
		Predicate<O> includeSecondClass,
		BiFunction<O, U, Result> combinedCheck
	) {
		final ParticleDefinition<Particle> particleDefinition0 = getParticleDefinition(particleClass0);
		if (particleDefinition0.getRriMapper() == null) {
			throw new IllegalStateException(particleClass0 + " must be registered with an RRI mapper.");
		}
		final ParticleDefinition<Particle> particleDefinition1 = getParticleDefinition(particleClass1);
		if (particleDefinition1.getRriMapper() == null) {
			throw new IllegalStateException(particleClass1 + " must be registered with an RRI mapper.");
		}

		var createCombinedTransitionRoutine = new CreateCombinedTransitionRoutine<>(
			RRIParticle.class,
			particleClass0,
			particleClass1,
			includeSecondClass,
			combinedCheck,
			in -> Optional.of(in.getRri().getAddress())
		);

		this.executeRoutine(createCombinedTransitionRoutine);
	}

	@Override
	public <I extends Particle, O extends Particle, U extends ReducerState> void createTransition(
		TransitionToken<I, O, U> transitionToken,
		TransitionProcedure<I, O, U> procedure
	) {
		if (scryptTransitionProcedures.containsKey(transitionToken)) {
			throw new IllegalStateException(transitionToken + " already created");
		}

		final ParticleDefinition<Particle> inputDefinition = getParticleDefinition(transitionToken.getInputClass());
		final ParticleDefinition<Particle> outputDefinition = getParticleDefinition(transitionToken.getOutputClass());

		final TransitionProcedure<Particle, Particle, ReducerState> transformedProcedure
			= new TransitionProcedure<Particle, Particle, ReducerState>() {
				@Override
				public PermissionLevel requiredPermissionLevel() {
					return procedure.requiredPermissionLevel();
				}

				@Override
				public Result precondition(Particle inputParticle, Particle outputParticle, ReducerState outputUsed) {
					// RRIs must be the same across RRI particle transitions
					if (inputDefinition.getRriMapper() != null && outputDefinition.getRriMapper() != null) {
						final RRI inputRRI = inputDefinition.getRriMapper().apply(inputParticle);
						final RRI outputRRI = outputDefinition.getRriMapper().apply(outputParticle);
						if (!inputRRI.equals(outputRRI)) {
							return Result.error("Input/Output RRIs not equal");
						}
					}

					return procedure.precondition((I) inputParticle, (O) outputParticle, (U) outputUsed);
				}

				@Override
				public InputOutputReducer<Particle, Particle, ReducerState> inputOutputReducer() {
					return (input, output, index, outputUsed) -> procedure.inputOutputReducer()
						.reduce((I) input, (O) output, index, (U) outputUsed);
				}

				@Override
				public SignatureValidator<Particle> inputSignatureRequired() {
					return i -> procedure.inputSignatureRequired().requiredSignature((I) i);
				}
			};

		scryptTransitionProcedures.put(transitionToken, transformedProcedure);
	}

	@Override
	public void executeRoutine(ConstraintRoutine routine) {
		routine.main(this);
	}
}
