package com.radixdlt.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;

/**
 * A logical update of the ledger, composed of either an up or down spin associated with a
 * particle, which represents state.
 */
@SerializerId2("radix.spun_particle")
public final class SpunParticle {

	@JsonProperty("particle")
	@DsonOutput(Output.ALL)
	private final Particle particle;

	private Spin spin;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	private SpunParticle() {
		this.particle = null;
		this.spin = null;
	}

	private SpunParticle(Particle particle, Spin spin) {
		Objects.requireNonNull(particle);
		Objects.requireNonNull(spin);

		this.particle = particle;
		this.spin = spin;
	}

	public static SpunParticle up(Particle particle) {
		return new SpunParticle(particle, Spin.UP);
	}

	public static SpunParticle down(Particle particle) {
		return new SpunParticle(particle, Spin.DOWN);
	}

	public static SpunParticle of(Particle particle, Spin spin) {
		return new SpunParticle(particle, spin);
	}

	public Particle getParticle() {
		return particle;
	}

	public Spin getSpin() {
		return spin;
	}

	public boolean isUp() {
		return this.spin.equals(Spin.UP);
	}

	public boolean isDown() {
		return this.spin.equals(Spin.DOWN);
	}

	@JsonProperty("spin")
	@DsonOutput(Output.ALL)
	private int getJsonSpin() {
		return this.spin.intValue();
	}

	@JsonProperty("spin")
	private void setJsonSpin(int spin) {
		this.spin = Spin.valueOf(spin);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof SpunParticle)) {
			return false;
		}

		SpunParticle spunParticle = (SpunParticle) obj;

		return this.particle.equals(spunParticle.particle) && this.spin.equals(spunParticle.spin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(particle, spin);
	}

	@Override
	public String toString() {
		EUID phid = (this.particle == null) ? null : this.particle.getHID();

		return String.format("%s[(%s):%s:%s:%s]", getClass().getSimpleName(),
				String.valueOf(phid), String.valueOf(spin), String.valueOf(particle),
				String.valueOf(this.particle != null ? particle.getDestinations() : "null"));
	}
}
