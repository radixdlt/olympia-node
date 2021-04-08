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

package com.radixdlt.atommodel.validators;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("v_u")
public final class UnregisteredValidatorParticle extends Particle {
	@JsonProperty("o")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress address;

	@JsonCreator
	public UnregisteredValidatorParticle(@JsonProperty("o") RadixAddress address) {
		this.address = Objects.requireNonNull(address, "address");
	}

	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.address);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof UnregisteredValidatorParticle)) {
			return false;
		}
		final var that = (UnregisteredValidatorParticle) obj;
		return Objects.equals(this.address, that.address);
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), getAddress());
	}
}
