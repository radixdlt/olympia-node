/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.client.core.atoms;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;

import java.util.Objects;

/**
 * Instruction which has been parsed and state checked by Radix Engine
 */
public final class SpunParticle {
	private final Particle particle;
	private final Spin spin;

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

	public <T extends Particle> T getParticle(Class<T> cls) {
		return cls.cast(particle);
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
		return String.format("%s[%s:%s]", getClass().getSimpleName(), spin, particle);
	}
}
