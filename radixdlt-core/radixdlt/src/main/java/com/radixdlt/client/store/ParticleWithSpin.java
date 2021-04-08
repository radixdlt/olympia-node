/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.store;

import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;

import static java.util.Objects.requireNonNull;

public class ParticleWithSpin {
	private final Particle particle;
	private final Spin spin;

	private ParticleWithSpin(Particle particle, Spin spin) {
		this.particle = particle;
		this.spin = spin;
	}

	public static ParticleWithSpin create(ParsedInstruction instruction) {
		return create(instruction.getParticle(), instruction.getSpin());
	}

	public static ParticleWithSpin up(Particle particle) {
		return create(particle, Spin.UP);
	}

	public static ParticleWithSpin down(Particle particle) {
		return create(particle, Spin.DOWN);
	}

	public static ParticleWithSpin create(Particle particle, Spin spin) {
		requireNonNull(particle);
		requireNonNull(spin);

		return new ParticleWithSpin(particle, spin);
	}

	public Particle getParticle() {
		return particle;
	}

	public Spin getSpin() {
		return spin;
	}

}
