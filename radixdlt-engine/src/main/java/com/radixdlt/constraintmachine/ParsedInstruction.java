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

package com.radixdlt.constraintmachine;

import java.util.Objects;

public final class ParsedInstruction {
	private final Particle particle;
	private final Spin spin;

	private ParsedInstruction(Particle particle, Spin spin) {
		Objects.requireNonNull(particle);
		Objects.requireNonNull(spin);

		this.particle = particle;
		this.spin = spin;
	}

	public static ParsedInstruction up(Particle particle) {
		return new ParsedInstruction(particle, Spin.UP);
	}

	public static ParsedInstruction down(Particle particle) {
		return new ParsedInstruction(particle, Spin.DOWN);
	}

	public static ParsedInstruction of(Particle particle, Spin spin) {
		return new ParsedInstruction(particle, spin);
	}

	public Particle getParticle() {
		return particle;
	}

	public <T extends Particle> T getParticle(Class<T> cls) {
		return cls.cast(this.particle);
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
		if (!(obj instanceof ParsedInstruction)) {
			return false;
		}

		ParsedInstruction parsedInstruction = (ParsedInstruction) obj;

		return Objects.equals(this.particle, parsedInstruction.particle) && Objects.equals(this.spin, parsedInstruction.spin);
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
