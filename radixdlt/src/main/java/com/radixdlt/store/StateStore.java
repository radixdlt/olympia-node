package com.radixdlt.store;

import com.radixdlt.atoms.Particle;
import java.util.Optional;
import java.util.Set;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;

/**
 *  A state that gives access to the state of a certain shard space
 */
public interface StateStore {

	/**
	 * Returns whether this state store supports any of the given destinations
	 * @param destinations the destinations to check
	 * @return true, if this state supports this address, false otherwise
	 */
	boolean supports(Set<EUID> destinations);

	/**
	 * Get the current spin of a particle. Returns an empty optional if
	 * the state provider does not know (for example, in sharded environments).
	 *
	 * @param particle the particle to get the spin of
	 * @return if known, the current spin of a particle, otherwise an empty optional
	 */
	Optional<Spin> getSpin(Particle particle);
}
