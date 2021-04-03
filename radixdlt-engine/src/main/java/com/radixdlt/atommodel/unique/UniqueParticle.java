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

package com.radixdlt.atommodel.unique;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;

@SerializerId2("radix.particles.unique")
public final class UniqueParticle extends Particle {
	@JsonProperty("name")
	@DsonOutput(DsonOutput.Output.ALL)
	private String name;

	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	UniqueParticle() {
		// For serializer
		super();
	}

	public UniqueParticle(String name, RadixAddress address) {
		this.name = Objects.requireNonNull(name);
		this.address = address;
	}

	public RRI getRRI() {
		return RRI.of(address, name);
	}

	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.address);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UniqueParticle)) {
			return false;
		}
		final var that = (UniqueParticle) obj;
		return Objects.equals(this.name, that.name)
			&& Objects.equals(this.address, that.address);
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), String.valueOf(name), String.valueOf(address));
	}
}
