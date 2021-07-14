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

import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.engine.RadixEngineException;

import java.util.List;
import java.util.function.BiFunction;

public interface EngineStore<M> extends SubstateStore {
	/**
	 * For verification
	 */
	interface EngineStoreInTransaction<M> extends CMStore {
		void storeTxn(Txn txn, List<REStateUpdate> instructions);
		void storeMetadata(M metadata);
	}
	interface TransactionEngineStoreConsumer<M, R> {
		R start(EngineStoreInTransaction<M> store) throws RadixEngineException;
	}
	<R> R transaction(TransactionEngineStoreConsumer<M, R> consumer) throws RadixEngineException;

	/**
	 * Deterministically computes a value from a list of particles of a given type.
	 * Must implement this until we get rid of optimistic concurrency.
	 *
	 * @param <V> the class of the state to reduce to
	 * @param initial the initial value of the state
	 * @param particleClass the particle class to reduce
	 * @return the computed, reduced state
	 */
	<V> V reduceUpParticles(
		V initial,
		BiFunction<V, Particle, V> outputReducer,
		SubstateDeserialization deserialization,
		Class<? extends Particle>... particleClass
	);
}
