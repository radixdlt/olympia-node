package com.radixdlt.client.core.atoms.particles;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("radix.spun_particle")
public class SpunParticle<T extends Particle> extends SerializableObject {

	@JsonProperty("particle")
	@DsonOutput(DsonOutput.Output.ALL)
	private final T particle;

	private transient Spin spin;

	private SpunParticle() {
		this.particle = null;
	}

	private SpunParticle(T particle, Spin spin) {
		this.particle = particle;
		this.spin = spin;
	}

	public static <T extends Particle> SpunParticle<T> up(T particle) {
		return new SpunParticle<>(particle, Spin.UP);
	}

	public static <T extends Particle> SpunParticle<T> down(T particle) {
		return new SpunParticle<>(particle, Spin.DOWN);
	}

	public static <T extends Particle> SpunParticle<T> of(T particle, Spin spin) {
		return new SpunParticle<>(particle, spin);
	}

	public Spin getSpin() {
		return spin;
	}

	public T getParticle() {
		return particle;
	}

	@JsonProperty("spin")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private int getJsonSpin() {
		return this.spin.intValue();
	}

	@JsonProperty("spin")
	private void setJsonSpin(int spin) {
		this.spin = Spin.valueOf(spin);
	}

	@Override
	public String toString() {
		String hid = (this.particle == null) ? "null" : this.particle.getHid().toString();
		return String.format("%s[%s:%s:%s:%s]", getClass().getSimpleName(), hid, String.valueOf(spin), String.valueOf(particle),
			String.valueOf(this.particle != null ? particle.getDestinations() : "null"));
	}
}
