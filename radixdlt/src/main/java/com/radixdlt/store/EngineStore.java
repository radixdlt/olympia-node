package com.radixdlt.store;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.CMAtom;

/**
 *  A state that gives access to the state of a certain shard space
 */
public interface EngineStore extends CMStore {
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
