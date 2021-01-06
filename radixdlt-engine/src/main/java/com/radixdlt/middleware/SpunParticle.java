/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
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
		// For serializer
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

		return Objects.equals(this.particle, spunParticle.particle) && Objects.equals(this.spin, spunParticle.spin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(particle, spin);
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s]", getClass().getSimpleName(),
				String.valueOf(spin), String.valueOf(particle),
				String.valueOf(this.particle != null ? particle.getDestinations() : "null"));
	}
}
