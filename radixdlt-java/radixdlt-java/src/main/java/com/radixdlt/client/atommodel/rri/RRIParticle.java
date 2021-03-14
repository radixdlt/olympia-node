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

package com.radixdlt.client.atommodel.rri;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("radix.particles.rri")
public final class RRIParticle extends Particle implements Accountable {
	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RRI rri;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long nonce;

	RRIParticle() {
		// Serializer only
		this.rri = null;
		this.nonce = 0;
	}

	public RRIParticle(RRI rri) {
		this.rri = Objects.requireNonNull(rri);
		this.nonce = 0;
	}

	@Override
	public Set<EUID> getDestinations() {
		return ImmutableSet.of(this.rri.getAddress().euid());
	}

	public RRI getRRI() {
		return rri;
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return Collections.singleton(rri.getAddress());
	}

	public long getNonce() {
		return nonce;
	}

	@Override
	public String toString() {
		return String.format("%s[(%s:%s)]",
			getClass().getSimpleName(), rri, nonce);
	}


	@Override
	public int hashCode() {
		return Objects.hash(this.rri, this.nonce);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof RRIParticle)) {
			return false;
		}
		final var that = (RRIParticle) obj;
		return this.nonce == that.nonce
			&& Objects.equals(this.rri, that.rri);
	}
}
