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

import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.constraintmachine.ParsedTransaction;
import com.radixdlt.constraintmachine.Particle;
import java.util.function.BiFunction;

/**
 *  A state that gives access to the state of a certain shard space
 */
public interface EngineStore<M> extends SubstateStore, CMStore {
	/**
	 * Stores the atom into this CMStore
	 */
	void storeAtom(Transaction txn, ParsedTransaction parsedTransaction);

	void storeMetadata(Transaction txn, M metadata);

	/**
	 * Deterministically computes a value from a list of particles of a given type.
	 * Must implement this until we get rid of optimistic concurrency.
	 *
	 * @param particleClass the particle class to reduce
	 * @param initial the initial value of the state
	 * @param <U> the particle class to reduce
	 * @param <V> the class of the state to reduce to
	 * @return the computed, reduced state
	 */
	<U extends Particle, V> V reduceUpParticles(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer
	);
}
