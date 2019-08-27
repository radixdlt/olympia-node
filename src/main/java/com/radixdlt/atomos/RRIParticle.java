package com.radixdlt.atomos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("radix.particles.rri")
public final class RRIParticle extends Particle {
	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI rri;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	private RRIParticle() {
	}

	public RRIParticle(RRI rri) {
		super(rri.getAddress().getUID());

		this.rri = rri;
		this.nonce = 0;
	}

	public RRI getRri() {
		return rri;
	}

	public long getNonce() {
		return nonce;
	}

	@Override
	public String toString() {
		return String.format("%s[(%s:%s)]",
			getClass().getSimpleName(), rri, nonce);
	}
}
