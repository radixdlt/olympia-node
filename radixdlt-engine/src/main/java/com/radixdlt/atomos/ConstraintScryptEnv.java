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
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Procedure;
import com.radixdlt.constraintmachine.ProcedureKey;
import com.radixdlt.constraintmachine.Procedures;

import java.util.HashMap;
import java.util.Map;

/**
 * SysCall environment for CMAtomOS Constraint Scrypts.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintScryptEnv implements Loader {
	private final ImmutableMap<Class<? extends Particle>, SubstateDefinition<? extends Particle>> particleDefinitions;

	private final Map<Class<? extends Particle>, SubstateDefinition<? extends Particle>> scryptParticleDefinitions;
	private final Map<ProcedureKey, Procedure> procedures;

	ConstraintScryptEnv(
		ImmutableMap<Class<? extends Particle>, SubstateDefinition<? extends Particle>> particleDefinitions
	) {
		this.particleDefinitions = particleDefinitions;
		this.scryptParticleDefinitions = new HashMap<>();
		this.procedures = new HashMap<>();
	}

	public Map<Class<? extends Particle>, SubstateDefinition<? extends Particle>> getScryptParticleDefinitions() {
		return scryptParticleDefinitions;
	}

	public Procedures getProcedures() {
		return new Procedures(procedures);
	}

	private <T extends Particle> boolean particleDefinitionExists(Class<T> particleClass) {
		return particleDefinitions.containsKey(particleClass) || scryptParticleDefinitions.containsKey(particleClass);
	}

	@Override
	public <T extends Particle> void substate(SubstateDefinition<T> substateDefinition) {
		var substateClass = substateDefinition.getSubstateClass();
		if (particleDefinitionExists(substateClass)) {
			throw new IllegalStateException("Substate " + substateClass + " is already registered");
		}

		scryptParticleDefinitions.put(substateClass, substateDefinition);
	}

	@Override
	public  void procedure(Procedure procedure) {
		var key = procedure.key();
		if (procedures.containsKey(key)) {
			throw new IllegalStateException(key + " already created");
		}
		procedures.put(key, procedure);
	}
}
