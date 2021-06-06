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
import com.radixdlt.constraintmachine.MethodProcedure;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.ProcedureKey;
import com.radixdlt.constraintmachine.Procedures;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.ShutdownAllProcedure;
import com.radixdlt.constraintmachine.UpProcedure;

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
	private final Map<ProcedureKey, MethodProcedure> procedures;

	ConstraintScryptEnv(
		ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions
	) {
		this.particleDefinitions = particleDefinitions;
		this.scryptParticleDefinitions = new HashMap<>();
		this.procedures = new HashMap<>();
	}

	public Map<Class<? extends Particle>, ParticleDefinition<Particle>> getScryptParticleDefinitions() {
		return scryptParticleDefinitions;
	}

	public Procedures getProcedures() {
		return new Procedures(procedures);
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
		if (procedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		procedures.put(key, shutdownAllProcedure);
	}

	@Override
	public <D extends Particle, S extends ReducerState> void createDownProcedure(DownProcedure<D, S> downProcedure) {
		var key = downProcedure.getDownProcedureKey();
		if (procedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		procedures.put(key, downProcedure);
	}

	@Override
	public <U extends Particle, S extends ReducerState> void createUpProcedure(UpProcedure<S, U> upProcedure) {
		var key = upProcedure.getUpProcedureKey();
		if (procedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		procedures.put(key, upProcedure);
	}

	@Override
	public <S extends ReducerState> void createEndProcedure(EndProcedure<S> endProcedure) {
		var key = endProcedure.getEndProcedureKey();
		if (procedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		procedures.put(key, endProcedure);
	}
}
