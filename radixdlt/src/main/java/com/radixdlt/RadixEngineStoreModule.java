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
import com.google.inject.name.Named;
import com.radixdlt.consensus.AddressBookGenesisHeaderProvider;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.middleware2.store.EngineAtomIndices;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.middleware2.store.CommandToBinaryConverter;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.CommittedCommandsReader;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.berkeley.NextCommittedLimitReachedException;
import com.radixdlt.universe.Universe;

import java.util.function.BiFunction;

public class RadixEngineStoreModule extends AbstractModule {
	private final int fixedNodeCount;

	public RadixEngineStoreModule(int fixedNodeCount) {
		this.fixedNodeCount = fixedNodeCount;
	}

	@Override
	protected void configure() {
		bind(new TypeLiteral<EngineStore<CommittedAtom>>() { }).to(CommittedAtomsStore.class).in(Scopes.SINGLETON);
		bind(CommittedCommandsReader.class).to(CommittedAtomsStore.class);
	}

	@Provides
	@Singleton
	private EngineStore<LedgerAtom> engineStore(CommittedAtomsStore committedAtomsStore) {
		return new EngineStore<LedgerAtom>() {
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
	private AddressBookGenesisHeaderProvider provider(
		AddressBook addressBook,
		@Named("self") ECKeyPair selfKey
	) {
		return new AddressBookGenesisHeaderProvider(
			selfKey.getPublicKey(),
			addressBook,
			fixedNodeCount
		);
	}

	@Provides
	@Singleton
	private BFTValidatorSet genesisValidatorSet(
		AddressBookGenesisHeaderProvider metadataProvider
	) {
		return metadataProvider.getGenesisValidatorSet();
	}

	@Provides
	private VerifiedLedgerHeaderAndProof genesisHeader(
		Serialization serialization,
		Universe universe,
		CommittedAtomsStore committedAtomsStore
	) throws LedgerAtomConversionException, SerializationException, NextCommittedLimitReachedException {
		final ClientAtom genesisAtom = ClientAtom.convertFromApiAtom(universe.getGenesis().get(0));
		byte[] payload = serialization.toDson(genesisAtom, Output.ALL);
		Command command = new Command(payload);
		VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(command.getHash());

		if (committedAtomsStore.getNextCommittedCommands(genesisLedgerHeader.getStateVersion() - 1, 1).isEmpty()) {
			ClientAtom clientAtom = serialization.fromDson(command.getPayload(), ClientAtom.class);
			CommittedAtom committedAtom = new CommittedAtom(clientAtom, genesisLedgerHeader.getStateVersion(), genesisLedgerHeader);
			committedAtomsStore.storeAtom(committedAtom);
		}

		return genesisLedgerHeader;
	}

	@Provides
	@Singleton
	private AtomIndexer buildAtomIndexer(Serialization serialization) {
		return atom -> EngineAtomIndices.from(atom, serialization);
	}

	@Provides
	@Singleton
	private CommittedAtomsStore committedAtomsStore(
		CommittedAtomSender committedAtomSender,
		LedgerEntryStore store,
		CommandToBinaryConverter commandToBinaryConverter,
		ClientAtomToBinaryConverter clientAtomToBinaryConverter,
		AtomIndexer atomIndexer,
		Serialization serialization
	) {
		return new CommittedAtomsStore(
			committedAtomSender,
			store,
			commandToBinaryConverter,
			clientAtomToBinaryConverter,
			atomIndexer,
			serialization
		);
	}

	@Provides
	@Named("magic")
	private int magic(Universe universe) {
		return universe.getMagic();
	}
}
