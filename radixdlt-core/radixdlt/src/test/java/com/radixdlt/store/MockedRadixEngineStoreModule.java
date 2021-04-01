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

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.atom.Atom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.utils.UInt256;

import java.util.List;

public class MockedRadixEngineStoreModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
	}

	@Provides
	@Singleton
	private EngineStore<LedgerAndBFTProof> engineStore(
		@Genesis List<Atom> genesisAtoms,
		Hasher hasher,
		Serialization serialization,
		@Genesis ImmutableList<ECKeyPair> genesisValidatorKeys
	) {
		var inMemoryEngineStore = new InMemoryEngineStore<LedgerAndBFTProof>();
		for (var genesisAtom : genesisAtoms) {
			byte[] payload = serialization.toDson(genesisAtom, DsonOutput.Output.ALL);
			Command command = new Command(payload);
			BFTValidatorSet validatorSet = BFTValidatorSet.from(genesisValidatorKeys.stream()
					.map(k -> BFTValidator.from(BFTNode.create(k.getPublicKey()), UInt256.ONE)));
			if (!inMemoryEngineStore.containsAtom(genesisAtom)) {
				var txn = inMemoryEngineStore.createTransaction();
				inMemoryEngineStore.storeAtom(txn, genesisAtom);
				txn.commit();
			}
		}
		return inMemoryEngineStore;
	}
}
