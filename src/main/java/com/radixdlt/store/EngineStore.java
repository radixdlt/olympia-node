/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store;

import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.identifiers.AID;
import com.radixdlt.constraintmachine.Particle;
import java.util.function.Consumer;

/**
 *  A state that gives access to the state of a certain shard space
 */
public interface EngineStore<T extends RadixEngineAtom> extends CMStore {
	/**
	 * Retrieves the atom containing the given spun particle.
	 * TODO: change to reactive streams interface
	 */
	void getAtomContaining(Particle particle, boolean isInput, Consumer<T> callback);

	/**
	 * Stores the atom into this CMStore
	 */
	void storeAtom(T atom);

	/**
	 * Deletes an atom and all it's dependencies
	 */
	void deleteAtom(AID atomId);
}
