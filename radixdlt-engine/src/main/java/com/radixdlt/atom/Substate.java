/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atom;

import com.radixdlt.constraintmachine.Particle;

import java.util.Objects;

public final class Substate {
	private final Particle particle;
	private final SubstateId substateId;

	public Substate(Particle particle, SubstateId substateId) {
		this.particle = particle;
		this.substateId = substateId;
	}

	public SubstateId getId() {
		return substateId;
	}

	public Particle getParticle() {
		return particle;
	}

	@Override
	public int hashCode() {
		return Objects.hash(particle, substateId);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Substate)) {
			return false;
		}

		var other = (Substate) o;
		return Objects.equals(this.particle, other.particle)
			&& Objects.equals(this.substateId, other.substateId);
	}
}