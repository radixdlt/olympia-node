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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.store.EngineAtomIndices.IndexType;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationUtils;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.store.LedgerEntryStoreResult;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntryStore;

import com.radixdlt.store.StoreIndex.LedgerIndexType;
import com.radixdlt.store.NextCommittedLimitReachedException;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.Longs;
import com.radixdlt.store.Transaction;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.Optional;

public final class CommittedAtomsStore implements EngineStore<CommittedAtom>, CommittedReader, RadixEngineAtomicCommitManager {
	private final Serialization serialization;
	private final AtomIndexer atomIndexer;
	private final LedgerEntryStore store;
	private final PersistentVertexStore persistentVertexStore;
	private final EventDispatcher<AtomCommittedToLedger> committedDispatcher;
	private final Hasher hasher;
	private Transaction transaction;

	public interface AtomIndexer {
		EngineAtomIndices getIndices(LedgerAtom atom);
	}

	public CommittedAtomsStore(
		LedgerEntryStore store,
		PersistentVertexStore persistentVertexStore,
		AtomIndexer atomIndexer,
		Serialization serialization,
		Hasher hasher,
		EventDispatcher<AtomCommittedToLedger> committedDispatcher
	) {
		this.store = Objects.requireNonNull(store);
		this.persistentVertexStore = Objects.requireNonNull(persistentVertexStore);
		this.atomIndexer = Objects.requireNonNull(atomIndexer);
		this.serialization = Objects.requireNonNull(serialization);
		this.hasher = hasher;
		this.committedDispatcher = Objects.requireNonNull(committedDispatcher);
	}

	private boolean particleExists(Particle particle, boolean isInput) {
		final byte[] indexableBytes = EngineAtomIndices.toByteArray(
		isInput ? EngineAtomIndices.IndexType.PARTICLE_DOWN : EngineAtomIndices.IndexType.PARTICLE_UP,
			Particle.euidOf(particle, hasher)
		);
		return store.contains(this.transaction, StoreIndex.LedgerIndexType.UNIQUE, new StoreIndex(indexableBytes), LedgerSearchMode.EXACT);
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

	private CommittedAtom deserialize(byte[] bytes) {
		try {
			return serialization.fromDson(bytes, CommittedAtom.class);
		} catch (DeserializeException e) {
			throw new IllegalStateException("Deserialization of Command failed", e);
		}
	}

	// TODO: Save proof in a separate index
	@Override
	public void storeAtom(CommittedAtom committedAtom) {
		EngineAtomIndices engineAtomIndices = atomIndexer.getIndices(committedAtom);

		LedgerEntryStoreResult result = store.store(
			this.transaction,
			committedAtom,
			engineAtomIndices.getUniqueIndices(),
			engineAtomIndices.getDuplicateIndices()
		);
		if (!result.isSuccess()) {
			throw new IllegalStateException("Unable to store atom");
		}

		final ImmutableSet<EUID> indicies = engineAtomIndices.getDuplicateIndices().stream()
			.filter(e -> e.getPrefix() == EngineAtomIndices.IndexType.DESTINATION.getValue())
			.map(e -> EngineAtomIndices.toEUID(e.asKey()))
			.collect(ImmutableSet.toImmutableSet());

		// Don't send event on genesis
		// TODO: this is a bit hacky
		if (committedAtom.getStateVersion() > 0) {
			committedDispatcher.dispatch(AtomCommittedToLedger.create(committedAtom, indicies));
		}
    }

	@Override
	public <U extends Particle, V> V compute(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer,
		BiFunction<V, U, V> inputReducer
	) {
		final String idForClass = serialization.getIdForClass(particleClass);
		final EUID numericClassId = SerializationUtils.stringToNumericID(idForClass);
		final byte[] indexableBytes = EngineAtomIndices.toByteArray(IndexType.PARTICLE_CLASS, numericClassId);
		final StoreIndex storeIndex = new StoreIndex(EngineAtomIndices.IndexType.PARTICLE_CLASS.getValue(), indexableBytes);
		SearchCursor cursor = store.search(LedgerIndexType.DUPLICATE, storeIndex, LedgerSearchMode.EXACT);

		V v = initial;
		while (cursor != null) {
			AID aid = cursor.get();
			Optional<CommittedAtom> ledgerEntry = store.get(aid);
			if (ledgerEntry.isPresent()) {
				CommittedAtom committedAtom = ledgerEntry.get();
				final ClientAtom clientAtom = committedAtom.getClientAtom();
				for (CMMicroInstruction cmMicroInstruction : clientAtom.getCMInstruction().getMicroInstructions()) {
					if (particleClass.isInstance(cmMicroInstruction.getParticle())
						&& cmMicroInstruction.isCheckSpin()) {
						if (cmMicroInstruction.getCheckSpin() == Spin.NEUTRAL) {
							v = outputReducer.apply(v, particleClass.cast(cmMicroInstruction.getParticle()));
						} else {
							v = inputReducer.apply(v, particleClass.cast(cmMicroInstruction.getParticle()));
						}
					}
				}
			}
			cursor = cursor.next();
		}
		return v;
	}

	public Optional<VerifiedLedgerHeaderAndProof> getLastVerifiedHeader() {
		return store.getLastHeaderProof();
	}

	@Override
	public Optional<VerifiedLedgerHeaderAndProof> getEpochVerifiedHeader(long epoch) {
		SearchCursor cursor = store.search(
			StoreIndex.LedgerIndexType.UNIQUE,
			new StoreIndex(IndexType.EPOCH_CHANGE.getValue(), Longs.toByteArray(epoch)),
			LedgerSearchMode.EXACT
		);
		if (cursor != null) {
			return store.get(cursor.get())
				.map(CommittedAtom::getHeaderAndProof)
				.map(Optional::orElseThrow); // Epoch change should always have a header/proof
		} else {
			return Optional.empty();
		}
	}

	public VerifiedCommandsAndProof getNextCommittedCommands(long stateVersion, int batchSize) throws NextCommittedLimitReachedException {
		ImmutableList<CommittedAtom> atoms = store.getNextCommittedAtoms(stateVersion, batchSize);
		if (atoms.isEmpty()) {
			return null;
		}
		final var nextHeader = atoms.get(atoms.size() - 1).getHeaderAndProof().orElseThrow();
		final var commands = atoms.stream()
			.map(a -> new Command(serialization.toDson(a.getClientAtom(), DsonOutput.Output.PERSIST)))
			.collect(ImmutableList.toImmutableList());
		return new VerifiedCommandsAndProof(commands, nextHeader);
	}

	@Override
	public VerifiedCommandsAndProof getNextCommittedCommands(DtoLedgerHeaderAndProof start, int batchSize)
		throws NextCommittedLimitReachedException {
		// TODO: verify start
		long stateVersion = start.getLedgerHeader().getAccumulatorState().getStateVersion();
		return this.getNextCommittedCommands(stateVersion, batchSize);
	}

	@Override
	public Spin getSpin(Particle particle) {
		if (particleExists(particle, true)) {
			return Spin.DOWN;
		} else if (particleExists(particle, false)) {
			return Spin.UP;
		}
		return Spin.NEUTRAL;
	}
}
