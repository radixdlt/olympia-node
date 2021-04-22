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
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.store.ImmutableIndex;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * SysCall environment for CMAtomOS Constraint Scrypts.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintScryptEnv implements SysCalls {
	public static final String NAME_REGEX = "[a-z0-9]+";
	public static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);
	private final ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions;

	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> scryptParticleDefinitions;
	private final Map<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> scryptTransitionProcedures;
	private final Set<String> systemNames;

	ConstraintScryptEnv(
		ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions,
		Set<String> systemNames
	) {
		this.particleDefinitions = particleDefinitions;
		this.systemNames = systemNames;

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
					final var rriId = particleDefinition.getRriMapper().apply(p);
					if (rriId == null) {
						return Result.error("rri cannot be null");
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
			new TransitionToken<>(REAddrParticle.class, particleClass, TypeToken.of(VoidReducerState.class)),
			new TransitionProcedure<>() {
				@Override
				public Result precondition(
					SubstateWithArg<REAddrParticle> in,
					O outputParticle,
					VoidReducerState outputUsed,
					ImmutableIndex index
				) {
					if (in.getArg().isEmpty()) {
						return Result.error("Rri must be created with a name");
					}
					var arg = in.getArg().get();
					if (!NAME_PATTERN.matcher(new String(arg)).matches()) {
						return Result.error("invalid rri name");
					}
					return Result.success();
				}

				@Override
				public InputOutputReducer<REAddrParticle, O, VoidReducerState> inputOutputReducer() {
					return (input, output, index, outputUsed) -> ReducerResult.complete(Unknown.create());
				}

				@Override
				public PermissionLevel requiredPermissionLevel(
					SubstateWithArg<REAddrParticle> in,
					O outputParticle,
					ImmutableIndex index
				) {
					var name = new String(in.getArg().orElseThrow());
					return systemNames.contains(name) || in.getSubstate().getAddr().isSystem()
						? PermissionLevel.SYSTEM : PermissionLevel.USER;
				}

				@Override
				public SignatureValidator<REAddrParticle, O> signatureValidator() {
					return (in, o, index, pubKey) -> pubKey.flatMap(k -> in.getArg().map(arg -> in.getSubstate().allow(k, arg)))
						.orElse(false);
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
			REAddrParticle.class,
			particleClass0,
			(rri, p) -> systemNames.contains(new String(rri.getArg().orElseThrow())) || rri.getSubstate().getAddr().isSystem()
				? PermissionLevel.SYSTEM : PermissionLevel.USER,
			particleClass1,
			includeSecondClass,
			combinedCheck,
			(in, o, index, pubKey) -> pubKey.flatMap(k -> in.getArg().map(arg -> in.getSubstate().allow(k, arg)))
				.orElse(false)
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
				public PermissionLevel requiredPermissionLevel(SubstateWithArg<Particle> i, Particle o, ImmutableIndex index) {
					var in = i.getArg()
						.map(arg -> SubstateWithArg.withArg((I) i.getSubstate(), arg))
						.orElseGet(() -> SubstateWithArg.noArg((I) i.getSubstate()));
					return procedure.requiredPermissionLevel(in, (O) o, index);
				}

				@Override
				public Result precondition(
					SubstateWithArg<Particle> in,
					Particle outputParticle,
					ReducerState outputUsed,
					ImmutableIndex index
				) {
					// RRIs must be the same across RRI particle transitions
					if (inputDefinition.getRriMapper() != null && outputDefinition.getRriMapper() != null) {
						final var inputRriId = inputDefinition.getRriMapper().apply(in.getSubstate());
						final var outputRriId = outputDefinition.getRriMapper().apply(outputParticle);
						if (!inputRriId.equals(outputRriId)) {
							return Result.error("Input/Output RRIs not equal");
						}
					}

					var inConvert = in.getArg()
						.map(arg -> SubstateWithArg.withArg((I) in.getSubstate(), arg))
						.orElseGet(() -> SubstateWithArg.noArg((I) in.getSubstate()));

					return procedure.precondition(inConvert, (O) outputParticle, (U) outputUsed, index);
				}

				@Override
				public InputOutputReducer<Particle, Particle, ReducerState> inputOutputReducer() {
					return (input, output, index, outputUsed) -> {
						var in = input.getArg()
							.map(arg -> SubstateWithArg.withArg((I) input.getSubstate(), arg))
							.orElseGet(() -> SubstateWithArg.noArg((I) input.getSubstate()));
						return procedure.inputOutputReducer()
							.reduce(in, (O) output, index, (U) outputUsed);
					};
				}

				@Override
				public SignatureValidator<Particle, Particle> signatureValidator() {
					return (i, o, index, pubKey) -> {
						var in = i.getArg()
							.map(arg -> SubstateWithArg.withArg((I) i.getSubstate(), arg))
							.orElseGet(() -> SubstateWithArg.noArg((I) i.getSubstate()));
						return procedure.signatureValidator().verify(in, (O) o, index, pubKey);
					};
				}
			};

		scryptTransitionProcedures.put(transitionToken, transformedProcedure);
	}

	@Override
	public void executeRoutine(ConstraintRoutine routine) {
		routine.main(this);
	}
}
