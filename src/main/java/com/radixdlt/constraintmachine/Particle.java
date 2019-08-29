package com.radixdlt.constraintmachine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
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
			return new Hash(Hash.hash256(Serialization.getDefault().toDson(this, Output.HASH)));
		} catch (Exception e) {
			throw new RuntimeException("Error generating hash: " + e, e);
		}
	}

	public Hash getHash() {
		return cachedHash.get();
	}

	@JsonProperty("hid")
	@DsonOutput(Output.API)
	public final EUID getHID() {
		return getHash().getID();
	}
}
