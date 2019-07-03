package com.radixdlt.atomos.mapper;

import java.util.Set;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;

/**
 * A mapper of {@link Particle}s to {@link RRI}s (i.e. indices).
 * @param <T>
 */
@FunctionalInterface
public interface ParticleToShardablesMapper<T extends Particle> {
	/**
	 * Get the {@link RRI} mapped to the given {@link Particle}
	 * @param particle The {@link Particle}
	 * @return The mapped RRI
	 */
	Set<RadixAddress> getDestinations(T particle);
}
