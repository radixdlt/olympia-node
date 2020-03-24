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

package com.radixdlt.constraintmachine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A content-identifiable, sub-state of the ledger.
 *
 * TODO: Remove serialization stuff out of here
 */
@SerializerId2("radix.particle")
public abstract class Particle {
	// TODO: Move this out and up to Atom level
	@JsonProperty("destinations")
	@DsonOutput(Output.ALL)
	private ImmutableSet<EUID> destinations;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	private final Supplier<Hash> cachedHash = Suppliers.memoize(this::doGetHash);

	public Particle() {
		this.destinations = ImmutableSet.of();
	}

	public Particle(EUID destination) {
		this.destinations = ImmutableSet.of(destination);
	}

	public Particle(ImmutableSet<EUID> destinations) {
		this.destinations = destinations;
	}

	public Set<EUID> getDestinations() {
		return destinations;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		if (o == this) {
			return true;
		}

		if (getClass().isInstance(o) && getHash().equals(((Particle) o).getHash())) {
			return true;
		}

		return false;
	}

	@Override
	public int hashCode() {
		return getHash().hashCode();
	}

	private Hash doGetHash() {
		try {
			return new Hash(Hash.hash256(DefaultSerialization.getInstance().toDson(this, Output.HASH)));
		} catch (Exception e) {
			throw new RuntimeException("Error generating hash: " + e, e);
		}
	}

	public Hash getHash() {
		return cachedHash.get();
	}

	@JsonProperty("hid")
	@DsonOutput(Output.API)
	public final EUID euid() {
		return getHash().euid();
	}
}
