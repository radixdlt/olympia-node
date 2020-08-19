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
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import java.util.Set;

@SerializerId2("radix.particles.registered_validator")
public class RegisteredValidatorParticle extends Particle {
	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	@JsonProperty("allowedDelegators")
	@DsonOutput(DsonOutput.Output.ALL)
	private Set<RadixAddress> allowedDelegators;

	@JsonProperty("url")
	@DsonOutput(DsonOutput.Output.ALL)
	private String url;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	private RegisteredValidatorParticle() {
		// for serializer
	}

	public RegisteredValidatorParticle(RadixAddress address, Set<RadixAddress> allowedDelegators, long nonce) {
		this(address, allowedDelegators, null, nonce);
	}

	public RegisteredValidatorParticle(RadixAddress address, Set<RadixAddress> allowedDelegators, String url, long nonce) {
		super(address.euid());
		this.address = address;
		this.allowedDelegators = allowedDelegators;
		this.url = url;
		this.nonce = nonce;
	}

	public boolean allowsDelegator(RadixAddress delegator) {
		return this.allowedDelegators.isEmpty() || this.allowedDelegators.contains(delegator);
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
		return allowedDelegators;
	}

	public String getUrl() {
		return url;
	}

	public long getNonce() {
		return nonce;
	}

	@Override
	public String toString() {
		return String.format(
			"%s[%s, %s, %s]",
			getClass().getSimpleName(), getAddress(), getUrl(), getAllowedDelegators()
		);
	}
}
