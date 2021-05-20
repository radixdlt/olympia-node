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
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.OutputAuthorization;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Procedures;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.InputAuthorization;
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SysCall environment for CMAtomOS Constraint Scrypts.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintScryptEnv implements SysCalls {
	private final ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions;

	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> scryptParticleDefinitions;
	private final Map<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> scryptTransitionProcedures;
	private final Map<Pair<Class<? extends Particle>, Class<? extends ReducerState>>, DownProcedure<Particle, ReducerState>> downProcedures;
	private final Map<Pair<Class<? extends ReducerState>, Class<? extends Particle>>, UpProcedure<ReducerState, Particle>> upProcedures;
	private final Map<Class, EndProcedure<ReducerState>> endProcedures;

	ConstraintScryptEnv(
		ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions
	) {
		this.particleDefinitions = particleDefinitions;
		this.scryptParticleDefinitions = new HashMap<>();
		this.scryptTransitionProcedures = new HashMap<>();
		this.downProcedures = new HashMap<>();
		this.upProcedures = new HashMap<>();
		this.endProcedures = new HashMap<>();
	}

	public Map<Class<? extends Particle>, ParticleDefinition<Particle>> getScryptParticleDefinitions() {
		return scryptParticleDefinitions;
	}

	public Procedures getProcedures() {
		return new Procedures(scryptTransitionProcedures, upProcedures, downProcedures, endProcedures);
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
				public PermissionLevel inputPermissionLevel(SubstateWithArg<Particle> i, ReadableAddrs index) {
					var in = i.getArg()
						.map(arg -> SubstateWithArg.withArg((I) i.getSubstate(), arg))
						.orElseGet(() -> SubstateWithArg.noArg((I) i.getSubstate()));
					return procedure.inputPermissionLevel(in, index);
				}

				@Override
				public Result precondition(
					SubstateWithArg<Particle> in,
					Particle outputParticle,
					ReducerState outputUsed,
					ReadableAddrs index
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
				public InputAuthorization<Particle> inputAuthorization() {
					return (i, index, pubKey) -> {
						var in = i.getArg()
							.map(arg -> SubstateWithArg.withArg((I) i.getSubstate(), arg))
							.orElseGet(() -> SubstateWithArg.noArg((I) i.getSubstate()));
						return procedure.inputAuthorization().verify(in, index, pubKey);
					};
				}

				@Override
				public OutputAuthorization<Particle> outputAuthorization() {
					return (o, index, pubKey) -> procedure.outputAuthorization().verify((O) o, index, pubKey);
				}
			};

		scryptTransitionProcedures.put(transitionToken, transformedProcedure);
	}

	@Override
	public <D extends Particle, S extends ReducerState> void createDownProcedure(DownProcedure<D, S> downProcedure) {
		var key = downProcedure.getDownProcedureKey();
		if (downProcedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		downProcedures.put(key, (DownProcedure<Particle, ReducerState>) downProcedure);
	}

	@Override
	public <U extends Particle, S extends ReducerState> void createUpProcedure(UpProcedure<S, U> upProcedure) {
		var key = upProcedure.getUpProcedureKey();
		if (upProcedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		upProcedures.put(key, (UpProcedure<ReducerState, Particle>) upProcedure);
	}

	@Override
	public <S extends ReducerState> void createEndProcedure(EndProcedure<S> endProcedure) {
		var key = endProcedure.getEndProcedureKey();
		if (endProcedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		endProcedures.put(key, (EndProcedure<ReducerState>) endProcedure);
	}


	@Override
	public void executeRoutine(ConstraintRoutine routine) {
		routine.main(this);
	}
}
