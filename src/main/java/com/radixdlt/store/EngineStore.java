package com.radixdlt.store;

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.Particle;
import java.util.function.Consumer;

/**
 *  A state that gives access to the state of a certain shard space
 */
public interface EngineStore extends CMStore {
	/**
	 * Retrieves the atom containing the given spun particle.
	 * TODO: change to reactive streams interface
	 */
	void getAtomContaining(Particle particle, boolean isInput, Consumer<Atom> callback);

	/**
	 * Stores the atom into this CMStore
	 */
	void storeAtom(Atom atom);

	/**
	 * Deletes an atom and all it's dependencies
	 */
	void deleteAtom(AID atomId);
}
