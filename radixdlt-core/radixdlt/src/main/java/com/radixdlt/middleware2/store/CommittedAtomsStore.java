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

package com.radixdlt.middleware2.store;

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.atom.Atom;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.AID;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntryStore;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.Optional;

public final class CommittedAtomsStore implements EngineStore<Atom, LedgerAndBFTProof>, RadixEngineAtomicCommitManager {
	private final LedgerEntryStore store;

	@Inject
	public CommittedAtomsStore(LedgerEntryStore store) {
		this.store = Objects.requireNonNull(store);
	}

	@Override
	public void startTransaction() {
	}

	@Override
	public void commitTransaction() {
	}

	@Override
	public void abortTransaction() {
	}

	@Override
	public void storeAtom(Transaction txn, Atom atom) {
		store.store(txn, atom);
	}

	@Override
	public void storeMetadata(Transaction txn, LedgerAndBFTProof metadata) {
		store.store(txn, metadata);
	}

	public boolean containsAID(AID aid) {
		return store.contains(aid);
	}

	@Override
	public boolean containsAtom(Atom atom) {
		return store.contains(atom.getAID());
	}

	@Override
	public <U extends Particle, V> V compute(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer
	) {
		return store.reduceUpParticles(particleClass, initial, outputReducer);
	}

	@Override
	public Transaction createTransaction() {
		return store.createTransaction();
	}

	@Override
	public Spin getSpin(Transaction txn, Particle particle) {
		return store.getSpin(txn, particle);
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction txn, HashCode particleHash) {
		return store.loadUpParticle(txn, particleHash);
	}
}
