/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.atommodel.validators;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import java.util.Set;

@SerializerId2("radix.particles.registered_validator")
public final class RegisteredValidatorParticle extends Particle implements Accountable, Ownable {
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

	public RegisteredValidatorParticle(RadixAddress address, ImmutableSet<RadixAddress> allowedDelegators, String url, long nonce) {
		this.address = Objects.requireNonNull(address);
		this.allowedDelegators = Objects.requireNonNull(allowedDelegators);
		this.url = url;
		this.nonce = nonce;
	}

	public RegisteredValidatorParticle(RadixAddress address, long nonce) {
		this(address, ImmutableSet.of(), null, nonce);
	}

	public RegisteredValidatorParticle(RadixAddress address, Set<RadixAddress> allowedDelegators, String url, long nonce) {
		this(address, ImmutableSet.copyOf(allowedDelegators), url, nonce);
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
		return this.allowedDelegators.isEmpty() || this.allowedDelegators.contains(delegator);
	}

	@Override
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
		return String.format("%s[%s %s %s]", getClass().getSimpleName(), this.address, this.url, this.allowedDelegators);
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return ImmutableSet.of(address);
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
}
