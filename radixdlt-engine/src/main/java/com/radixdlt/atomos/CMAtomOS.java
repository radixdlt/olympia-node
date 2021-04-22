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
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.identifiers.RadixAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import com.radixdlt.constraintmachine.Particle;

import java.util.stream.Collectors;

/**
 * Implementation of the AtomOS interface on top of a UTXO based Constraint Machine.
 */
// FIXME: rawtypes
@SuppressWarnings("rawtypes")
public final class CMAtomOS {

	private static final ParticleDefinition<Particle> VOID_PARTICLE_DEF = ParticleDefinition.builder()
		.staticValidation(v -> {
			throw new UnsupportedOperationException("Should not ever call here");
		})
		.allowTransitionsFromOutsideScrypts()
		.build();

	private static final ParticleDefinition<Particle> RRI_PARTICLE_DEF = ParticleDefinition.<REAddrParticle>builder()
		.staticValidation(rri -> {

			return Result.success();
		})
		.rriMapper(REAddrParticle::getAddr)
		.virtualizeUp(v -> true)
		.allowTransitionsFromOutsideScrypts()
		.build();

	private final Function<RadixAddress, Result> addressChecker;
	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions = new HashMap<>();
	private final ImmutableMap.Builder<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>>
		proceduresBuilder = new ImmutableMap.Builder<>();
	private final Set<String> systemNames;

	public CMAtomOS(
		Function<RadixAddress, Result> addressChecker,
		Set<String> systemNames
	) {
		// RRI particle is a low level particle managed by the OS used for the management of all other resources
		this.particleDefinitions.put(VoidParticle.class, VOID_PARTICLE_DEF);
		this.particleDefinitions.put(REAddrParticle.class, RRI_PARTICLE_DEF);
		this.addressChecker = addressChecker;
		this.systemNames = systemNames;
	}

	public CMAtomOS() {
		this(address -> Result.success(), Set.of());
	}

	public void load(ConstraintScrypt constraintScrypt) {
		var constraintScryptEnv = new ConstraintScryptEnv(
			ImmutableMap.copyOf(particleDefinitions),
			addressChecker,
			systemNames
		);
		constraintScrypt.main(constraintScryptEnv);
		this.particleDefinitions.putAll(constraintScryptEnv.getScryptParticleDefinitions());
		this.proceduresBuilder.putAll(constraintScryptEnv.getScryptTransitionProcedures());
	}

	public Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> buildTransitionProcedures() {
		final var procedures = proceduresBuilder.build();
		return procedures::get;
	}

	public Function<Particle, Result> buildParticleStaticCheck() {
		final ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions
			= ImmutableMap.copyOf(this.particleDefinitions);
		return p -> {
			final ParticleDefinition<Particle> particleDefinition = particleDefinitions.get(p.getClass());
			if (particleDefinition == null) {
				return Result.error("Unknown particle type: " + p.getClass());
			}

			final Function<Particle, Result> staticValidation = particleDefinition.getStaticValidation();
			final Result staticCheckResult = staticValidation.apply(p);
			if (staticCheckResult.isError()) {
				return staticCheckResult;
			}

			return Result.success();
		};
	}

	public Predicate<Particle> virtualizedUpParticles() {
		Map<? extends Class<? extends Particle>, Predicate<Particle>> virtualizedParticles = particleDefinitions.entrySet().stream()
			.filter(def -> def.getValue().getVirtualizeSpin() != null)
			.collect(Collectors.toMap(Map.Entry::getKey, def -> def.getValue().getVirtualizeSpin()));

		return p -> {
			var virtualizer = virtualizedParticles.get(p.getClass());
			return virtualizer != null && virtualizer.test(p);
		};
	}
}
