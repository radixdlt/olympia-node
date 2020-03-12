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
