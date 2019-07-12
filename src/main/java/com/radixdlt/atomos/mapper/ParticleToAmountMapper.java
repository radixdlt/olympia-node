package com.radixdlt.atomos.mapper;

import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;

/**
 * A mapper of {@link Particle} to their fungible integer amounts
 */
@FunctionalInterface
public interface ParticleToAmountMapper<T extends Particle> {
	/**
	 * The integer "amount" represented by the given Particle
	 * @param particle The Particle
	 * @return The "amount" of that Particle
	 */
	UInt256 amount(T particle);
}
