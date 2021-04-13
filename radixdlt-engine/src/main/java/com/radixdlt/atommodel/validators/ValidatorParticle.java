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

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;

public final class ValidatorParticle extends Particle {
	private final RadixAddress address;
	private final boolean registeredForNextEpoch;
	private final String name;
	private final String url;

	public ValidatorParticle(RadixAddress address, boolean registeredForNextEpoch) {
		this(address, registeredForNextEpoch, "", "");
	}

	public ValidatorParticle(
		RadixAddress address,
		boolean registeredForNextEpoch,
		String name,
		String url
	) {
		this.address = Objects.requireNonNull(address);
		this.registeredForNextEpoch = registeredForNextEpoch;
		this.name = Objects.requireNonNull(name);
		this.url = Objects.requireNonNull(url);
	}

	public boolean isRegisteredForNextEpoch() {
		return registeredForNextEpoch;
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.address, this.registeredForNextEpoch, this.name, this.url);
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
			&& Objects.equals(this.name, that.name)
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
