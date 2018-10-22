package com.radixdlt.client.core.atoms.particles;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.crypto.ECPublicKey;

/**
 * Particle which stores time related aspects of an atom.
 */
@SerializerId2("CHRONOPARTICLE")
public class ChronoParticle extends Particle {
	@JsonProperty("timestamps")
	@DsonOutput(Output.ALL)
	private Map<String, Long> timestamps;

	private Spin spin;

	ChronoParticle() {
		// Empty constructor for serializer
	}

	public ChronoParticle(long timestamp) {
		this.spin = Spin.UP;
		this.timestamps = Collections.singletonMap("default", timestamp);
	}

	@Override
	public Spin getSpin() {
		return spin;
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return Collections.emptySet();
	}

	public Long getTimestamp() {
		return timestamps.get("default");
	}

	@JsonProperty("spin")
	@DsonOutput(value = {Output.WIRE, Output.API, Output.PERSIST})
	private int getJsonSpin() {
		return this.spin.ordinalValue();
	}

	@JsonProperty("spin")
	private void setJsonSpin(int spin) {
		this.spin = Spin.valueOf(spin);
	}
}
