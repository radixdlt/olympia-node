package com.radixdlt.store;

import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.engine.RadixEngineAtom;
import java.util.function.Consumer;

/**
 *  A state that gives access to the state of a certain shard space
 */
public interface EngineStore<T extends RadixEngineAtom> extends CMStore {
	/**
	 * Retrieves the atom containing the given spun particle.
	 * TODO: change to reactive streams interface
	 */
	void getAtomContaining(SpunParticle spunParticle, Consumer<T> callback);

	/**
	 * Stores the atom into this CMStore
	 */
	void storeAtom(T atom);

	/**
	 * Deletes an atom and all it's dependencies
	 */
	void deleteAtom(T atom);
}
