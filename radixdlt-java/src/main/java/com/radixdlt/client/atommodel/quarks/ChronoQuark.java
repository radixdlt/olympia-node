package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.Quark;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

import java.util.Objects;

/**
 * A quark that keeps a specific timestamp.
 * Currently used to keep atom-specific timestamps in TimestampParticle.
 */
@SerializerId2("CHRONOQUARK")
public final class ChronoQuark extends Quark {
	@JsonProperty("timestampKey")
	@DsonOutput(DsonOutput.Output.ALL)
	private String timestampKey;

	@JsonProperty("timestampValue")
	@DsonOutput(DsonOutput.Output.ALL)
	private long timestampValue;

	private ChronoQuark() {
	}

	public ChronoQuark(String timestampkey, long timestampValue) {
		this.timestampKey = Objects.requireNonNull(timestampkey);
		this.timestampValue = timestampValue;
	}

	public String getTimestampKey() {
		return this.timestampKey;
	}

	public long getTimestampValue() {
		return this.timestampValue;
	}
}

