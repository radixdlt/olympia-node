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

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * A read-only view of a specific LedgerEntryStore
 */
public interface LedgerEntryStoreView {
	/**
	 * Checks whether the given aid is contained in this view
	 * @param aid The aid
	 * @return Whether the given aid is contained in this view
	 */
	boolean contains(AID aid);

	/**
	 * Gets the atom associated with a certain aid
	 * @param aid The aid
	 * @return The atom associated with the given aid (if any)
	 */
	Optional<ClientAtom> get(AID aid);

	/**
	 * Gets the last committed atom aid
	 * TODO: Remove optional
	 * @return The last committed atom aid
	 */
	Optional<VerifiedLedgerHeaderAndProof> getLastHeader();

	Optional<VerifiedLedgerHeaderAndProof> getEpochHeader(long epoch);

	/**
	 * Searches for a certain index.
	 *
	 * @param index The index
	 * @return The resulting ledger cursor
	 */
	SearchCursor search(StoreIndex index);

	<U extends Particle, V> V reduceUpParticles(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer
	);

	/**
	 * Checks whether a certain index is contained in this ledger.
	 *
	 * @param index The index
	 * @return The resulting ledger cursor
	 */
	boolean contains(Transaction tx, StoreIndex index);

	Spin getSpin(Transaction tx, Particle particle);

	/**
	 * Retrieve a chunk of {@link ClientAtom} with state version greater than the given one
	 * in sequential order.
	 * @param stateVersion the state version to use as a search parameter
	 * @return ledger entries satisfying the constraints
	 */
	VerifiedCommandsAndProof getNextCommittedAtoms(long stateVersion);
}
