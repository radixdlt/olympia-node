package com.radixdlt.store;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.common.EUID;
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
	 * Get the current spin of a particle. Must call supports() to see if this
	 * store supports the given particle. If not, the return value of getSpin() is
	 * undefined.
	 *
	 * @param particle the particle to get the spin of
	 * @return the current spin of a particle
	 */
	Spin getSpin(Particle particle);
}
