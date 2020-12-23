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
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.CMStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.store.SpinStateMachine;
import java.util.stream.Collectors;

/**
 * Implementation of the AtomOS interface on top of a UTXO based Constraint Machine.
 */
public final class CMAtomOS {
	private static final ParticleDefinition<Particle> VOID_PARTICLE_DEF = ParticleDefinition.builder()
		.addressMapper(v -> {
			throw new UnsupportedOperationException("Should not ever call here");
		})
		.staticValidation(v -> {
			throw new UnsupportedOperationException("Should not ever call here");
		})
		.allowTransitionsFromOutsideScrypts()
		.build();

	private static final ParticleDefinition<Particle> RRI_PARTICLE_DEF = ParticleDefinition.<RRIParticle>builder()
		.singleAddressMapper(rri -> rri.getRri().getAddress())
		.staticValidation(rri -> Result.success())
		.rriMapper(RRIParticle::getRri)
		.virtualizeSpin(v -> v.getNonce() == 0 ? Spin.UP : null)
		.allowTransitionsFromOutsideScrypts()
		.build();

	private final Function<RadixAddress, Result> addressChecker;
	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions = new HashMap<>();
	private final ImmutableMap.Builder<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>>
		proceduresBuilder = new ImmutableMap.Builder<>();

	public CMAtomOS(Function<RadixAddress, Result> addressChecker) {
		// RRI particle is a low level particle managed by the OS used for the management of all other resources
		this.particleDefinitions.put(VoidParticle.class, VOID_PARTICLE_DEF);
		this.particleDefinitions.put(RRIParticle.class, RRI_PARTICLE_DEF);
		this.addressChecker = addressChecker;
	}

	public CMAtomOS() {
		this(address -> Result.success());
	}

	public void load(ConstraintScrypt constraintScrypt) {
		ConstraintScryptEnv constraintScryptEnv = new ConstraintScryptEnv(
			ImmutableMap.copyOf(particleDefinitions),
			addressChecker
		);
		constraintScrypt.main(constraintScryptEnv);
		this.particleDefinitions.putAll(constraintScryptEnv.getScryptParticleDefinitions());
		this.proceduresBuilder.putAll(constraintScryptEnv.getScryptTransitionProcedures());
	}

	public Function<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> buildTransitionProcedures() {
		final ImmutableMap<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> procedures = proceduresBuilder.build();
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

			final Function<Particle, Set<RadixAddress>> mapper = particleDefinition.getAddressMapper();
			final Set<EUID> destinations = mapper.apply(p).stream().map(RadixAddress::euid).collect(Collectors.toSet());

			if (!destinations.containsAll(p.getDestinations())) {
				return Result.error("Address destinations does not contain all destinations");
			}

			if (!p.getDestinations().containsAll(destinations)) {
				return Result.error("Destinations does not contain all Address destinations");
			}

			return Result.success();
		};
	}

	public UnaryOperator<CMStore> buildVirtualLayer() {
		Map<? extends Class<? extends Particle>, Function<Particle, Spin>> virtualizedParticles = particleDefinitions.entrySet().stream()
			.filter(def -> def.getValue().getVirtualizeSpin() != null)
			.collect(Collectors.toMap(Map.Entry::getKey, def -> def.getValue().getVirtualizeSpin()));
		return base -> particle -> {
			Spin curSpin = base.getSpin(particle);

			Function<Particle, Spin> virtualizer = virtualizedParticles.get(particle.getClass());
			if (virtualizer != null) {
				Spin virtualizedSpin = virtualizer.apply(particle);
				if (virtualizedSpin != null && SpinStateMachine.isAfter(virtualizedSpin, curSpin)) {
					return virtualizedSpin;
				}
			}

			return curSpin;
		};
	}
}
