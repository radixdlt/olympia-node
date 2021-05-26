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
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Procedures;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.ShutdownAllProcedure;
import com.radixdlt.constraintmachine.UpProcedure;
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
	private final Map<Pair<Class<? extends Particle>, Class<? extends ReducerState>>, DownProcedure<Particle, ReducerState>> downProcedures;
	private final Map<Pair<Class<? extends Particle>, Class<? extends ReducerState>>, ShutdownAllProcedure<Particle, ReducerState>> shutdownAllProcedures;
	private final Map<Pair<Class<? extends ReducerState>, Class<? extends Particle>>, UpProcedure<ReducerState, Particle>> upProcedures;
	private final Map<Class, EndProcedure<ReducerState>> endProcedures;

	ConstraintScryptEnv(
		ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions
	) {
		this.particleDefinitions = particleDefinitions;
		this.scryptParticleDefinitions = new HashMap<>();
		this.downProcedures = new HashMap<>();
		this.shutdownAllProcedures = new HashMap<>();
		this.upProcedures = new HashMap<>();
		this.endProcedures = new HashMap<>();
	}

	public Map<Class<? extends Particle>, ParticleDefinition<Particle>> getScryptParticleDefinitions() {
		return scryptParticleDefinitions;
	}

	public Procedures getProcedures() {
		return new Procedures(upProcedures, downProcedures, shutdownAllProcedures, endProcedures);
	}

	private <T extends Particle> boolean particleDefinitionExists(Class<T> particleClass) {
		return particleDefinitions.containsKey(particleClass) || scryptParticleDefinitions.containsKey(particleClass);
	}

	@Override
	public <T extends Particle> void registerParticle(Class<T> particleClass, ParticleDefinition<T> particleDefinition) {
		if (particleDefinitionExists(particleClass)) {
			throw new IllegalStateException("Particle " + particleClass + " is already registered");
		}
		Objects.requireNonNull(particleDefinition, "particleDefinition");

		scryptParticleDefinitions.put(particleClass, (ParticleDefinition<Particle>) particleDefinition);
	}

	@Override
	public <D extends Particle, S extends ReducerState> void createShutDownAllProcedure(
		ShutdownAllProcedure<D, S> shutdownAllProcedure
	) {
		var key = shutdownAllProcedure.getKey();
		if (shutdownAllProcedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		shutdownAllProcedures.put(key, (ShutdownAllProcedure<Particle, ReducerState>) shutdownAllProcedure);
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
}
