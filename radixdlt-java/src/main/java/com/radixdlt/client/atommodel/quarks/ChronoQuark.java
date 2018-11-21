package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.Quark;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.time.Timestamps;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A quark that keeps a specific timestamp.
 * Currently used to keep atom-specific timestamps in TimestampParticle.
 */
@SerializerId2("CHRONOQUARK")
public final class ChronoQuark extends Quark {
	@JsonProperty("timestamps")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, Long> timestamps = new HashMap<>();

	private ChronoQuark() {
	}

	public ChronoQuark(String timestampKey, long timestampValue) {
		Objects.requireNonNull(timestampKey, "timestampKey is required");

		this.timestamps.put(timestampKey, timestampValue);
	}

	public long getTimestamp() {
		return getTimestamp(Timestamps.DEFAULT);
	}

	public long getTimestamp(String type) {
		Objects.requireNonNull(type, "type is required");

		return this.timestamps.getOrDefault(type, 0L);
	}

	public void setTimestamp(String type, long timestamp) {
		Objects.requireNonNull(type, "type is required");

		this.timestamps.put(type, timestamp);
	}

	public Set<String> getTimestampTypes() {
		return this.timestamps.keySet();
	}
}

