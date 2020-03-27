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

package org.radix.atoms.particles.conflict;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.AID;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.containers.BasicContainer;

import java.util.Set;

@SerializerId2("conflict.particle_conflict")
public final class ParticleConflict extends BasicContainer {
	@Override
	public short VERSION() {
		return 100;
	}

	@JsonProperty("dataPointer")
	@DsonOutput(Output.ALL)
	private DataPointer dataPointer;

	@JsonProperty("atomIds")
	@DsonOutput(Output.ALL)
	private Set<AID> atomIds;

	public ParticleConflict(DataPointer dataPointer, Set<AID> atomIds) {
		super();
		this.dataPointer = dataPointer;
		this.atomIds = ImmutableSet.copyOf(atomIds);
	}

	@Override
	public String toString() {
		return this.getClass().toString() + ": " + dataPointer.toString();
	}

	public DataPointer getDataPointer() {
		return dataPointer;
	}

	public Set<AID> getAtomIds() {
		return this.atomIds;
	}

}