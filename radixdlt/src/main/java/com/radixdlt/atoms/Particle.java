package com.radixdlt.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput.Output;
import java.util.Set;
import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

/**
 * A content-identifiable, sub-state of the ledger.
 */
@SerializerId2("radix.particle")
public abstract class Particle extends BasicContainer {
	@JsonProperty("destinations")
	@DsonOutput(Output.ALL)
	private ImmutableSet<EUID> destinations;

	public Particle() {
		this.destinations = ImmutableSet.of();
	}

	public Particle(EUID destination) {
		this.destinations = ImmutableSet.of(destination);
	}

	public Particle(ImmutableSet<EUID> destinations) {
		this.destinations = destinations;
	}

	@Override
	public short VERSION() {
		return 100;
	}

	public Set<EUID> getDestinations() {
		return destinations;
	}

	@Override
	public abstract String toString();
}
