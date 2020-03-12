package com.radixdlt.client.core.atoms.particles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.identifiers.RadixAddress;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import com.radixdlt.crypto.Hash;

/**
 * A logical action on the ledger
 */
@SerializerId2("radix.particle")
public abstract class Particle {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

	@JsonProperty("destinations")
	@DsonOutput(Output.ALL)
	private ImmutableSet<EUID> destinations;

	public Particle() {
		this.destinations = ImmutableSet.of();
	}

	public Particle(EUID destination) {
		Objects.requireNonNull(destination);
		this.destinations = ImmutableSet.of(destination);
	}

	public Particle(Set<EUID> destinations) {
		Objects.requireNonNull(destinations);
		this.destinations = ImmutableSet.copyOf(destinations);
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
		try {
			return Serialize.getInstance().toDson(this, Output.HASH);
		} catch (SerializationException e) {
			throw new IllegalStateException("Failed to serialize", e);
		}
	}

	public final Hash getHash() {
		return new Hash(toDson());
	}

	public final EUID getHid() {
		return this.getHash().euid();
	}

	public Set<EUID> getDestinations() {
		return destinations;
	}

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
