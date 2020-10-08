package com.radixdlt.atommodel.system;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("radix.particles.system_particle")
public final class SystemParticle extends Particle {
	@JsonProperty("epoch")
	@DsonOutput(DsonOutput.Output.ALL)
	private long epoch;

	@JsonProperty("view")
	@DsonOutput(DsonOutput.Output.ALL)
	private long view;

	@JsonProperty("timestamp")
	@DsonOutput(DsonOutput.Output.ALL)
	private long timestamp;

	SystemParticle() {
		// For serializer
		super();
	}

	public SystemParticle(long epoch, long view, long timestamp) {
		super();
		this.epoch = epoch;
		this.view = view;
		this.timestamp = timestamp;
	}

	public long getEpoch() {
		return epoch;
	}

	public long getView() {
		return view;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
