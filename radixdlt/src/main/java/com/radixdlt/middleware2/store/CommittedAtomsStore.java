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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.identifiers.AID;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.function.Consumer;

@Singleton
public class CommittedAtomsStore implements EngineStore<CommittedAtom> {
	private static final Logger log = LogManager.getLogger();

	private final AtomIndexer atomIndexer;
	private final LedgerEntryStore store;
	private final AtomToBinaryConverter atomToBinaryConverter;
	private final AtomicLong stateVersion = new AtomicLong(0);

	public interface AtomIndexer {
		EngineAtomIndices getIndices(LedgerAtom atom);
	}

	@Inject
	public CommittedAtomsStore(
		LedgerEntryStore store,
		AtomToBinaryConverter atomToBinaryConverter,
		AtomIndexer atomIndexer
	) {
		this.store = store;
		this.atomToBinaryConverter = atomToBinaryConverter;
		this.atomIndexer = atomIndexer;
	}

	@Override
	public void getAtomContaining(Particle particle, boolean isInput, Consumer<CommittedAtom> callback) {
		Optional<CommittedAtom> atomOptional = getAtomByParticle(particle, isInput);
		atomOptional.ifPresent(callback);
	}

	private Optional<CommittedAtom> getAtomByParticle(Particle particle, boolean isInput) {
		final byte[] indexableBytes = EngineAtomIndices.toByteArray(
		isInput ? EngineAtomIndices.IndexType.PARTICLE_DOWN : EngineAtomIndices.IndexType.PARTICLE_UP,
			particle.euid()
		);
		SearchCursor cursor = store.search(StoreIndex.LedgerIndexType.UNIQUE, new StoreIndex(indexableBytes), LedgerSearchMode.EXACT);
		if (cursor != null) {
			return store.get(cursor.get()).flatMap(ledgerEntry ->  Optional.of(atomToBinaryConverter.toAtom(ledgerEntry.getContent())));
		} else {
			log.debug("getAtomByParticle returned empty result");
			return Optional.empty();
		}
	}

	@Override
	public void storeAtom(CommittedAtom committedAtom) {
		byte[] binaryAtom = atomToBinaryConverter.toLedgerEntryContent(committedAtom);
		VertexMetadata vertexMetadata = committedAtom.getVertexMetadata();
		LedgerEntry ledgerEntry = new LedgerEntry(binaryAtom, vertexMetadata.getStateVersion(), committedAtom.getAID());
		EngineAtomIndices engineAtomIndices = atomIndexer.getIndices(committedAtom);

		// TODO: Replace Store + Commit with a single commit
		// TODO: How it's done depends on how mempool and prepare phases are implemented
        store.store(ledgerEntry, engineAtomIndices.getUniqueIndices(), engineAtomIndices.getDuplicateIndices());
        store.commit(committedAtom.getAID());
    }

	// TODO: Move into storeAtom when epoch change logic moved into RadixEngine
	public void storeVertexMetadata(VertexMetadata vertexMetadata) {
		stateVersion.set(vertexMetadata.getStateVersion());
	}

	// TODO: Move into more approrpriate place
	public ImmutableSet<EUID> getIndicies(CommittedAtom committedAtom) {
		EngineAtomIndices engineAtomIndices = atomIndexer.getIndices(committedAtom);
		return engineAtomIndices.getDuplicateIndices().stream()
			.filter(e -> e.getPrefix() == EngineAtomIndices.IndexType.DESTINATION.getValue())
			.map(e -> EngineAtomIndices.toEUID(e.asKey()))
			.collect(ImmutableSet.toImmutableSet());
	}

	/**
	 * Retrieve the committed atoms in the store starting at a given state version (exclusively)
	 * @param stateVersion the state version to start on (exclusively)
	 * @param limit limit to number of atoms to return
	 * @return list of committed atoms
	 */
	public List<CommittedAtom> getCommittedAtoms(long stateVersion, int limit) {
		ImmutableList<LedgerEntry> entries = store.getNextCommittedLedgerEntries(stateVersion, limit);
		return entries
				.stream()
				.map(LedgerEntry::getContent)
				.map(atomToBinaryConverter::toAtom)
				.collect(ImmutableList.toImmutableList());
	}

	/**
	 * Retrieve the current state version of the store
	 * @return the state version of the store
	 */
	public long getStateVersion() {
		return stateVersion.get();
	}

	@Override
	public void deleteAtom(AID atomId) {
		throw new UnsupportedOperationException("Delete operation is not supported by Ledger interface");
	}

	@Override
	public Spin getSpin(Particle particle) {
		if (getAtomByParticle(particle, true).isPresent()) {
			return Spin.DOWN;
		} else if (getAtomByParticle(particle, false).isPresent()) {
			return Spin.UP;
		}
		return Spin.NEUTRAL;
	}
}
