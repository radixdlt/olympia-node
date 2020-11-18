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
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.middleware2.store.EngineAtomIndices;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.middleware2.store.CommandToBinaryConverter;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.berkeley.NextCommittedLimitReachedException;
import com.radixdlt.sync.CommittedReader;

import java.util.function.BiFunction;

public class RadixEngineStoreModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(new TypeLiteral<EngineStore<CommittedAtom>>() { }).to(CommittedAtomsStore.class).in(Scopes.SINGLETON);
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
	private BFTValidatorSet validatorSet(
		@LastEpochProof VerifiedLedgerHeaderAndProof lastEpochProof
	) {
		return lastEpochProof.getNextValidatorSet().orElseThrow(() -> new IllegalStateException("Genesis has no validator set"));
	}

	@Provides
	@Singleton
	@LastProof
	VerifiedLedgerHeaderAndProof lastProof(
		CommittedAtomsStore store,
		VerifiedCommandsAndProof genesisCheckpoint // TODO: remove once genesis creation resolved
	) {
		return store.getLastVerifiedHeader()
			.orElse(genesisCheckpoint.getHeader());
	}

	@Provides
	@Singleton
	@LastEpochProof
	VerifiedLedgerHeaderAndProof lastEpochProof(
		@LastProof VerifiedLedgerHeaderAndProof lastProof,
		CommittedAtomsStore store
	) {
		if (lastProof.isEndOfEpoch()) {
			return lastProof;
		}
		return store.getEpochVerifiedHeader(lastProof.getEpoch()).orElseThrow();
	}

	@Provides
	@Singleton
	private BFTConfiguration initialConfig(
		@LastEpochProof VerifiedLedgerHeaderAndProof lastEpochProof,
		BFTValidatorSet validatorSet,
		Hasher hasher
	) {
		UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(lastEpochProof.getRaw());
		VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
		LedgerHeader nextLedgerHeader = LedgerHeader.create(
			lastEpochProof.getEpoch() + 1,
			View.genesis(),
			lastEpochProof.getAccumulatorState(),
			lastEpochProof.timestamp()
		);
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
		return new BFTConfiguration(validatorSet, verifiedGenesisVertex, genesisQC);
	}

	@Provides
	@Singleton
	private CommittedAtomsStore committedAtomsStore(
		CommittedAtomSender committedAtomSender,
		LedgerEntryStore store,
		CommandToBinaryConverter commandToBinaryConverter,
		ClientAtomToBinaryConverter clientAtomToBinaryConverter,
		AtomIndexer atomIndexer,
		Serialization serialization,
		Hasher hasher,
		VerifiedCommandsAndProof genesisCheckpoint
	) throws NextCommittedLimitReachedException, DeserializeException {
		final CommittedAtomsStore atomsStore = new CommittedAtomsStore(
			committedAtomSender,
			store,
			commandToBinaryConverter,
			clientAtomToBinaryConverter,
			atomIndexer,
			serialization,
			hasher
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
