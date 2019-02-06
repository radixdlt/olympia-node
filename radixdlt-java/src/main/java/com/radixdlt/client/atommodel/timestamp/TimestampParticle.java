package com.radixdlt.client.atommodel.timestamp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.HashMap;
import java.util.Map;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

/**
 * Particle which stores time related aspects of an atom.
 */
@SerializerId2("TIMESTAMPPARTICLE")
public class TimestampParticle extends Particle {
	@JsonProperty("timestamps")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, Long> timestamps = new HashMap<>();

	private TimestampParticle() {
	}

	public TimestampParticle(long timestamp) {
		this.timestamps.put("default", timestamp);
	}

	public long getTimestamp() {
		return timestamps.get("default");
	}
}
