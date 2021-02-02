/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.middleware2.store.EngineAtomIndices;
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.middleware2.store.CommandToBinaryConverter;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.NextCommittedLimitReachedException;
import com.radixdlt.sync.CommittedReader;

import java.util.function.BiFunction;

public class RadixEngineStoreModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(new TypeLiteral<EngineStore<CommittedAtom>>() { }).to(CommittedAtomsStore.class).in(Scopes.SINGLETON);
		bind(RadixEngineAtomicCommitManager.class).to(CommittedAtomsStore.class);
		bind(CommittedReader.class).to(CommittedAtomsStore.class);
	}

	@Provides
	@Singleton
	private EngineStore<LedgerAtom> engineStore(CommittedAtomsStore committedAtomsStore) {
		return new EngineStore<>() {
			@Override
			public void storeAtom(LedgerAtom ledgerAtom) {
				if (!(ledgerAtom instanceof CommittedAtom)) {
					throw new IllegalStateException("Should not be storing atoms which aren't committed");
				}

				CommittedAtom committedAtom = (CommittedAtom) ledgerAtom;
				committedAtomsStore.storeAtom(committedAtom);
			}

			@Override
			public <U extends Particle, V> V compute(
				Class<U> particleClass,
				V initial,
				BiFunction<V, U, V> outputReducer,
				BiFunction<V, U, V> inputReducer
			) {
				return committedAtomsStore.compute(particleClass, initial, outputReducer, inputReducer);
			}

			@Override
			public Spin getSpin(Particle particle) {
				return committedAtomsStore.getSpin(particle);
			}
		};
	}

	@Provides
	@Singleton
	private AtomIndexer buildAtomIndexer(Serialization serialization, Hasher hasher) {
		return atom -> EngineAtomIndices.from(atom, serialization, hasher);
	}

	@Provides
	@Singleton
	private CommittedAtomsStore committedAtomsStore(
		LedgerEntryStore store,
		PersistentVertexStore persistentVertexStore,
		CommandToBinaryConverter commandToBinaryConverter,
		ClientAtomToBinaryConverter clientAtomToBinaryConverter,
		AtomIndexer atomIndexer,
		Serialization serialization,
		Hasher hasher,
		EventDispatcher<AtomCommittedToLedger> committedDispatcher,
		VerifiedCommandsAndProof genesisCheckpoint
	) throws NextCommittedLimitReachedException, DeserializeException {
		final CommittedAtomsStore atomsStore = new CommittedAtomsStore(
			store,
			persistentVertexStore,
			commandToBinaryConverter,
			clientAtomToBinaryConverter,
			atomIndexer,
			serialization,
			hasher,
			committedDispatcher
		);

		if (atomsStore.getNextCommittedCommands(genesisCheckpoint.getHeader().getStateVersion() - 1, 1) == null) {
			for (Command command : genesisCheckpoint.getCommands()) {
				ClientAtom clientAtom = serialization.fromDson(command.getPayload(), ClientAtom.class);
				CommittedAtom committedAtom = new CommittedAtom(
					clientAtom,
					genesisCheckpoint.getHeader().getStateVersion(),
					genesisCheckpoint.getHeader()
				);
				atomsStore.storeAtom(committedAtom);
			}
		}

		return atomsStore;
	}
}
