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

import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.ShutdownAllIndex;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.identifiers.REAddr;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 *  A state that gives access to the state of a certain shard space
 */
public interface EngineStore<M> extends SubstateStore {
	/**
	 * Hack for atomic transaction, better to implement
	 * whole function in single interface in future.
	 */
	interface Transaction {
		default void commit() {
		}

		default void abort() {
		}

		default <T> T unwrap() {
			return null;
		}
	}

	Transaction createTransaction();

	boolean isVirtualDown(Transaction txn, SubstateId substateId);

	Optional<Particle> loadUpParticle(
		Transaction txn,
		SubstateId substateId,
		SubstateDeserialization deserialization
	);

	CloseableCursor<RawSubstateBytes> openIndexedCursor(Transaction txn, ShutdownAllIndex index);

	/**
	 * Stores the atom into this CMStore
	 */
	void storeTxn(Transaction dbTxn, Txn txn, List<REStateUpdate> instructions);

	Optional<Particle> loadAddr(
		Transaction dbTxn,
		REAddr addr,
		SubstateDeserialization deserialization
	);

	void storeMetadata(Transaction txn, M metadata);

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
