/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("radix.spun_particle")
public class SpunParticle<T extends Particle> {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

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
		String hid = (this.particle == null) ? "null" : this.particle.euid().toString();
		return String.format("%s[%s:%s:%s:%s]", getClass().getSimpleName(), hid, String.valueOf(spin), String.valueOf(particle),
			String.valueOf(this.particle != null ? particle.getDestinations() : "null"));
	}
}
