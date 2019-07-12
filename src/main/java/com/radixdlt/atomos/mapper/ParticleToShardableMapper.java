package com.radixdlt.atomos.mapper;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;

/**
 * A mapper of {@link Particle}s to {@link RRI}s (i.e. indices).
 * @param <T>
 */
@FunctionalInterface
public interface ParticleToShardableMapper<T extends Particle> {
	/**
	 * Get the {@link RRI} mapped to the given {@link Particle}
	 * @param particle The {@link Particle}
	 * @return The mapped RRI
	 */
	RadixAddress getDestination(T particle);
}
