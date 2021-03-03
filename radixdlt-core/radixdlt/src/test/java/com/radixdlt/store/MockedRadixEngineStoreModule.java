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

package com.radixdlt.store;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.GenesisValidatorSetProvider;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.checkpoint.Genesis;

public class MockedRadixEngineStoreModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
	}

	@Provides
	@Singleton
	private EngineStore<LedgerAtom> engineStore(
		@Genesis Atom atom,
		Hasher hasher,
		Serialization serialization,
		GenesisValidatorSetProvider initialValidatorSetProvider
	) {
		InMemoryEngineStore<LedgerAtom> inMemoryEngineStore = new InMemoryEngineStore<>();
		final ClientAtom genesisAtom = ClientAtom.convertFromApiAtom(atom, hasher);
		byte[] payload = serialization.toDson(genesisAtom, DsonOutput.Output.ALL);
		Command command = new Command(payload);
		VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(
			hasher.hash(command),
			initialValidatorSetProvider.genesisValidatorSet()
		);
		CommittedAtom committedAtom = new CommittedAtom(
			genesisAtom,
			genesisLedgerHeader.getStateVersion(),
			genesisLedgerHeader
		);
		if (!inMemoryEngineStore.containsAtom(committedAtom)) {
			inMemoryEngineStore.storeAtom(committedAtom);
		}
		return inMemoryEngineStore;
	}

	@Provides
	private RadixEngineAtomicCommitManager atomicCommitManager() {
		return new RadixEngineAtomicCommitManager() {
			@Override
			public void startTransaction() {
				// no-op
			}

			@Override
			public void commitTransaction() {
				// no-op
			}

			@Override
			public void abortTransaction() {
				// no-op
			}

			@Override
			public void save(VerifiedVertexStoreState vertexStoreState) {
				// no-op
			}
		};
	}
}
