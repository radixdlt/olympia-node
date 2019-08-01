package com.radixdlt.store;

import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;
import java.util.Optional;
import java.util.Set;

/**
 * Read only store interface for Constraint Machine validation
 */
public interface CMStore {

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
