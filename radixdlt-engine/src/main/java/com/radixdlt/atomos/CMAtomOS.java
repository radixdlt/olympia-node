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
import com.radixdlt.constraintmachine.Procedures;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.VirtualSubstateDeserialization;

import java.util.HashMap;
import java.util.Map;

public final class CMAtomOS {
	private final Map<Class<? extends Particle>, SubstateDefinition<? extends Particle>> substateDefinitions = new HashMap<>();
	private Procedures procedures = Procedures.empty();

	public CMAtomOS() {
	}

	public void load(ConstraintScrypt constraintScrypt) {
		var constraintScryptEnv = new ConstraintScryptEnv(ImmutableMap.copyOf(substateDefinitions));
		constraintScrypt.main(constraintScryptEnv);
		substateDefinitions.putAll(constraintScryptEnv.getScryptParticleDefinitions());
		procedures = procedures.combine(constraintScryptEnv.getProcedures());
	}

	public Procedures getProcedures() {
		return procedures;
	}

	public SubstateDeserialization buildSubstateDeserialization() {
		return new SubstateDeserialization(substateDefinitions.values());
	}

	public SubstateSerialization buildSubstateSerialization() {
		return new SubstateSerialization(substateDefinitions.values());
	}

	public VirtualSubstateDeserialization buildVirtualSubstateDeserialization() {
		return new VirtualSubstateDeserialization(substateDefinitions.values());
	}
}
