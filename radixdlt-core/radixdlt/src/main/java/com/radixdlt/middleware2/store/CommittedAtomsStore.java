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
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntryStore;

import com.radixdlt.sync.CommittedReader;
import com.radixdlt.store.Transaction;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.Optional;

public final class CommittedAtomsStore implements EngineStore<CommittedAtom, LedgerProof>,
	CommittedReader, RadixEngineAtomicCommitManager {
	private final LedgerEntryStore store;
	private final PersistentVertexStore persistentVertexStore;
	private Transaction transaction;

	@Inject
	public CommittedAtomsStore(
		LedgerEntryStore store,
		PersistentVertexStore persistentVertexStore
	) {
		this.store = Objects.requireNonNull(store);
		this.persistentVertexStore = Objects.requireNonNull(persistentVertexStore);
	}

	@Override
	public void startTransaction() {
		this.transaction = store.createTransaction();
	}

	@Override
	public void commitTransaction() {
		this.transaction.commit();
		this.transaction = null;
	}

	@Override
	public void abortTransaction() {
		this.transaction.abort();
		this.transaction = null;
	}

	@Override
	public void save(VerifiedVertexStoreState vertexStoreState) {
		persistentVertexStore.save(this.transaction, vertexStoreState);
	}

	@Override
	public void storeAtom(CommittedAtom committedAtom) {
		var result = store.store(this.transaction, committedAtom);
		if (!result.isSuccess()) {
			throw new IllegalStateException("Unable to store atom");
		}
	}

	@Override
	public void storeMetadata(LedgerProof metadata) {
		store.store(this.transaction, metadata);
	}

	public boolean containsAID(AID aid) {
		return store.contains(aid);
	}

	@Override
	public boolean containsAtom(CommittedAtom atom) {
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

	public Optional<LedgerProof> getLastVerifiedHeader() {
		return store.getLastHeader();
	}

	@Override
	public Optional<LedgerProof> getEpochVerifiedHeader(long epoch) {
		return store.getEpochHeader(epoch);
	}

	public VerifiedCommandsAndProof getNextCommittedCommands(long start) {
		return this.store.getNextCommittedAtoms(start);
	}

	@Override
	public VerifiedCommandsAndProof getNextCommittedCommands(DtoLedgerHeaderAndProof start) {
		// TODO: verify start
		long stateVersion = start.getLedgerHeader().getAccumulatorState().getStateVersion();
		return this.getNextCommittedCommands(stateVersion);
	}

	@Override
	public Spin getSpin(Particle particle) {
		return store.getSpin(this.transaction, particle);
	}

	@Override
	public Optional<Particle> loadUpParticle(HashCode particleHash) {
		return store.loadUpParticle(this.transaction, particleHash);
	}
}
