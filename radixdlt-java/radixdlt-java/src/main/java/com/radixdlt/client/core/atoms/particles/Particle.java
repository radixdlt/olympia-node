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

package com.radixdlt.client.core.atoms.particles;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.Set;

import com.google.common.hash.HashCode;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.RadixAddress;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

/**
 * A logical action on the ledger
 */
@SerializerId2("radix.particle")
public abstract class Particle {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	public Particle() {
		// Nothing for now
	}

	public final Set<RadixAddress> getShardables() {
		Set<RadixAddress> addresses = new HashSet<>();

		if (this instanceof Accountable) {
			Accountable a = (Accountable) this;
			addresses.addAll(a.getAddresses());
		}

		if (this instanceof Identifiable) {
			Identifiable i = (Identifiable) this;
			addresses.add(i.getRRI().getAddress());
		}

		return new HashSet<>(addresses);
	}

	public final byte[] toDson() {
		return Serialize.getInstance().toDson(this, Output.HASH);
	}

	public final HashCode getHash() {
		return HashUtils.sha256(toDson());
	}

	public final EUID euid() {
		return EUID.fromHash(this.getHash());
	}

	public abstract Set<EUID> getDestinations();

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Particle)) {
			return false;
		}

		Particle particle = (Particle) o;
		return this.getHash().equals(particle.getHash());
	}

	@Override
	public int hashCode() {
		return this.getHash().hashCode();
	}
}
