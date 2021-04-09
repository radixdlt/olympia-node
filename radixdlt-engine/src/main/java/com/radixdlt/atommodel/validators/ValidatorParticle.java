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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("v")
public final class ValidatorParticle extends Particle {
	@JsonProperty("o")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress address;

	@JsonProperty("r")
	@DsonOutput(DsonOutput.Output.ALL)
	private final boolean registeredForNextEpoch;

	@JsonProperty("u")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String url;

	public ValidatorParticle(RadixAddress address, boolean registeredForNextEpoch) {
		this(address, registeredForNextEpoch, null);
	}

	@JsonCreator
	public ValidatorParticle(
		@JsonProperty("o") RadixAddress address,
		@JsonProperty("r") boolean registeredForNextEpoch,
		@JsonProperty("u") String url
	) {
		this.address = Objects.requireNonNull(address);
		this.registeredForNextEpoch = registeredForNextEpoch;
		this.url = url;
	}

	public boolean isRegisteredForNextEpoch() {
		return registeredForNextEpoch;
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.address, this.registeredForNextEpoch, this.url);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ValidatorParticle)) {
			return false;
		}
		final var that = (ValidatorParticle) obj;
		return Objects.equals(this.address, that.address)
			&& this.registeredForNextEpoch == that.registeredForNextEpoch
			&& Objects.equals(this.url, that.url);
	}

	@Override
	public String toString() {
		return String.format(
			"%s[%s, %s, %s]",
			getClass().getSimpleName(), getAddress(), registeredForNextEpoch, getUrl()
		);
	}
}
