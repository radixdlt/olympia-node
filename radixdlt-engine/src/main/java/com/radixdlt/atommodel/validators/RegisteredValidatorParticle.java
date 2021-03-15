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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import java.util.Set;

@SerializerId2("radix.particles.registered_validator")
public final class RegisteredValidatorParticle extends Particle {
	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress address;

	@JsonProperty("allowedDelegators")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<RadixAddress> allowedDelegators;

	@JsonProperty("url")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String url;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long nonce;

	RegisteredValidatorParticle() {
		// Serializer only
		this.address = null;
		this.allowedDelegators = null;
		this.url = null;
		this.nonce = 0;
	}

	public RegisteredValidatorParticle(RadixAddress address, ImmutableSet<RadixAddress> allowedDelegators, long nonce) {
		this(address, allowedDelegators, null, nonce);
	}

	public RegisteredValidatorParticle(RadixAddress address, ImmutableSet<RadixAddress> allowedDelegators, String url, long nonce) {
		this.address = Objects.requireNonNull(address);
		this.allowedDelegators = Objects.requireNonNull(allowedDelegators);
		this.url = url;
		this.nonce = nonce;
	}

	public RegisteredValidatorParticle(RadixAddress address, long nonce) {
		this(address, ImmutableSet.of(), null, nonce);
	}

	public RegisteredValidatorParticle copyWithNonce(long nonce) {
		return new RegisteredValidatorParticle(
			this.address,
			this.allowedDelegators,
			this.url,
			nonce
		);
	}

	@Override
	public Set<EUID> getDestinations() {
		return ImmutableSet.of(this.address.euid());
	}

	public boolean allowsDelegator(RadixAddress delegator) {
		return this.allowedDelegators == null || this.allowedDelegators.isEmpty() || this.allowedDelegators.contains(delegator);
	}

	public boolean equalsIgnoringNonce(RegisteredValidatorParticle other) {
		return Objects.equals(address, other.address)
			&& Objects.equals(allowedDelegators, other.allowedDelegators)
			&& Objects.equals(url, other.url);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public Set<RadixAddress> getAllowedDelegators() {
		return allowedDelegators == null ? Set.of() : allowedDelegators;
	}

	public String getUrl() {
		return url;
	}

	public long getNonce() {
		return nonce;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.address, this.allowedDelegators, this.url, this.nonce);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof RegisteredValidatorParticle)) {
			return false;
		}
		final var that = (RegisteredValidatorParticle) obj;
		return this.nonce == that.nonce
			&& Objects.equals(this.address, that.address)
			&& Objects.equals(this.allowedDelegators, that.allowedDelegators)
			&& Objects.equals(this.url, that.url);
	}

	@Override
	public String toString() {
		return String.format(
			"%s[%s, %s, %s]",
			getClass().getSimpleName(), getAddress(), getUrl(), getAllowedDelegators()
		);
	}
}
