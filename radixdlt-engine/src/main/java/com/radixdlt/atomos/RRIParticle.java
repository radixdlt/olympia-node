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

import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("radix.particles.rri")
public final class RRIParticle extends Particle {
	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI rri;

	RRIParticle() {
		// Serializer only
	}

	public RRIParticle(RRI rri) {
		this.rri = rri;
	}

	@Override
	public Set<EUID> getDestinations() {
		return ImmutableSet.of(this.rri.getAddress().euid());
	}

	public RRI getRri() {
		return rri;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.rri);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof RRIParticle)) {
			return false;
		}
		final var that = (RRIParticle) obj;
		return Objects.equals(this.rri, that.rri);
	}

	@Override
	public String toString() {
		return String.format("%s[(%s)]",
			getClass().getSimpleName(), rri);
	}
}
