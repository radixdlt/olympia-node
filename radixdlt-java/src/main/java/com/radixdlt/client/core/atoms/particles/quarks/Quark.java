package com.radixdlt.client.core.atoms.particles.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerDummy;

/**
 * A distinct property of a {@link com.radixdlt.client.core.atoms.particles.Particle}
 */
public abstract class Quark {
	// Placeholder for the serializer ID
	@JsonProperty("serializer")
	@DsonOutput({DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private SerializerDummy serializer = SerializerDummy.DUMMY;
}
