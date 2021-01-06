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

package com.radixdlt.client.application.translate;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.Objects;

/**
 * Identifier for particle state for a particular address.
 */
public final class ShardedParticleStateId {
	private final Class<? extends Particle> particleClass;
	private final RadixAddress address;

	private ShardedParticleStateId(Class<? extends Particle> particleClass, RadixAddress address) {
		Objects.requireNonNull(particleClass);
		Objects.requireNonNull(address);

		this.particleClass = particleClass;
		this.address = address;
	}

	public static ShardedParticleStateId of(Class<? extends Particle> stateClass, RadixAddress address) {
		return new ShardedParticleStateId(stateClass, address);
	}

	/**
	 * Retrieves the type of application state needed for this requirement
	 *
	 * @return the type of application state
	 */
	public Class<? extends Particle> particleClass() {
		return this.particleClass;
	}

	/**
	 * Retrieves the shardable address which needs to be queried to construct the application state
	 *
	 * @return the shardable address
	 */
	public RadixAddress address() {
		return this.address;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ShardedParticleStateId)) {
			return false;
		}

		ShardedParticleStateId r = (ShardedParticleStateId) o;
		return r.particleClass.equals(particleClass) && r.address.equals(address);
	}

	@Override
	public int hashCode() {
		return Objects.hash(particleClass, address);
	}

	@Override
	public String toString() {
		return address + "/" + particleClass.getSimpleName();
	}
}
