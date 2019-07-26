package com.radixdlt.store;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.CMAtom;
import java.util.Optional;
import java.util.Set;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;

/**
 *  A state that gives access to the state of a certain shard space
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

	/**
	 * Retrieves the atom containing the given spun particle.
	 * TODO: remove this method and combine with getSpin
	 */
	ImmutableAtom getAtomContaining(SpunParticle spunParticle);

	/**
	 * Stores the atom into this CMStore
	 */
	void storeAtom(CMAtom atom, ImmutableMap<String, Object> computed);
}
