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
import com.radixdlt.consensus.AddressBookValidatorSetProvider;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.MempoolNextCommandGenerator;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.execution.RadixEngineExecutor;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.SharedMempool;
import com.radixdlt.mempool.SubmissionControl;
import com.radixdlt.mempool.SubmissionControlImpl;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.converters.AtomToClientAtomConverter;
import com.radixdlt.middleware2.network.MessageCentralLedgerSync;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.middleware2.store.EngineAtomIndices;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.CursorStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreView;
import com.radixdlt.store.berkeley.BerkeleyCursorStore;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.StateSyncNetwork;
import com.radixdlt.syncer.SyncServiceRunner;
import com.radixdlt.syncer.SyncServiceRunner.SyncedAtomSender;
import com.radixdlt.syncer.SyncedEpochExecutor;
import com.radixdlt.syncer.SyncedEpochExecutor.CommittedStateSyncSender;
import com.radixdlt.syncer.SyncedEpochExecutor.SyncService;
import com.radixdlt.universe.Universe;
import java.util.Objects;

/**
 * Module which manages synchronization of state
 * TODO: Split out Executor (Radix Engine) logic
 */
public class SyncerModule extends AbstractModule {
	private static final int BATCH_SIZE = 100;

	private final RuntimeProperties runtimeProperties;

	public SyncerModule(RuntimeProperties runtimeProperties) {
		this.runtimeProperties = Objects.requireNonNull(runtimeProperties);
	}

	@Override
	protected void configure() {
		bind(new TypeLiteral<SyncedExecutor<CommittedAtom>>() { }).to(SyncedEpochExecutor.class).in(Scopes.SINGLETON);
		bind(Mempool.class).to(SharedMempool.class).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EngineStore<CommittedAtom>>() { }).to(CommittedAtomsStore.class).in(Scopes.SINGLETON);
		bind(AtomToBinaryConverter.class).toInstance(new AtomToBinaryConverter(DefaultSerialization.getInstance()));

		// Database
		bind(LedgerEntryStore.class).to(BerkeleyLedgerEntryStore.class);
		bind(LedgerEntryStoreView.class).to(BerkeleyLedgerEntryStore.class);
		bind(CursorStore.class).to(BerkeleyCursorStore.class);
	}

	@Provides
	@Singleton
	private AtomToClientAtomConverter converter() {
		return atom -> {
			try {
				return ClientAtom.convertFromApiAtom(atom);
			} catch (LedgerAtomConversionException e) {
				throw new AtomConversionException(e.getDataPointer(), e);
			}
		};
	}

	@Provides
	@Singleton
	NextCommandGenerator nextCommandGenerator(
		Mempool mempool
	) {
		return new MempoolNextCommandGenerator(mempool);
	}

	@Provides
	@Singleton
	private AddressBookValidatorSetProvider addressBookValidatorSetProvider(
		AddressBook addressBook,
		@Named("self") ECKeyPair selfKey
	) {
		final int fixedNodeCount = runtimeProperties.get("consensus.fixed_node_count", 1);

		return new AddressBookValidatorSetProvider(
			selfKey.getPublicKey(),
			addressBook,
			fixedNodeCount,
			(epoch, validators) -> {
				/*
				Builder<BFTValidator> validatorSetBuilder = ImmutableList.builder();
				Random random = new Random(epoch);
				List<Integer> indices = IntStream.range(0, validators.size()).boxed().collect(Collectors.toList());
				// Temporary mechanism to get some deterministic random set of validators
				for (long i = 0; i < epoch; i++) {
					random.nextInt(validators.size());
				}
				int randInt = random.nextInt(validators.size());
				int validatorSetSize = randInt + 1;

				for (int i = 0; i < validatorSetSize; i++) {
					int index = indices.remove(random.nextInt(indices.size()));
					BFTValidator validator = validators.get(index);
					validatorSetBuilder.add(validator);
				}

				ImmutableList<BFTValidator> validatorList = validatorSetBuilder.build();

				return BFTValidatorSet.from(validatorList);
				*/
				return BFTValidatorSet.from(validators);
			}
		);
	}

	@Provides
	@Singleton
	private SyncService syncService(SyncServiceRunner runner) {
		return runner::syncToVersion;
	}


	@Provides
	@Singleton
	private SyncedAtomSender syncedAtomSender(SyncedEpochExecutor syncedEpochExecutor) {
		return syncedEpochExecutor::execute;
	}

	@Provides
	@Singleton
	private SyncServiceRunner syncServiceRunner(
		RadixEngineExecutor executor,
		SyncedAtomSender syncedAtomSender,
		AddressBook addressBook,
		StateSyncNetwork stateSyncNetwork
	) {
		return new SyncServiceRunner(
			executor,
			stateSyncNetwork,
			addressBook,
			syncedAtomSender,
			BATCH_SIZE,
			10
		);
	}

	@Provides
	@Singleton
	private SyncedEpochExecutor syncedEpochExecutor(
		Mempool mempool,
		RadixEngineExecutor executor,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		AddressBookValidatorSetProvider validatorSetProvider,
		SyncService syncService,
		AddressBook addressBook,
		StateSyncNetwork stateSyncNetwork,
		SystemCounters counters
	) {
		final long viewsPerEpoch = runtimeProperties.get("epochs.views_per_epoch", 100L);
		return new SyncedEpochExecutor(
			0L,
			mempool,
			executor,
			committedStateSyncSender,
			epochChangeSender,
			validatorSetProvider::getValidatorSet,
			View.of(viewsPerEpoch),
			syncService,
			counters
		);
	}

	@Provides
	@Singleton
	SubmissionControl submissionControl(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		Serialization serialization,
		AtomToClientAtomConverter converter,
		SubmissionControlSender submissionControlSender
	) {
		return new SubmissionControlImpl(
			mempool,
			radixEngine,
			serialization,
			converter,
			submissionControlSender
		);
	}

	@Provides
	@Singleton
	private StateSyncNetwork stateSyncNetwork(
		Universe universe,
		MessageCentral messageCentral
	) {
		return new MessageCentralLedgerSync(
			universe,
			messageCentral
		);
	}

	@Provides
	@Singleton
	private AtomIndexer buildAtomIndexer(Serialization serialization) {
		return atom -> EngineAtomIndices.from(atom, serialization);
	}
}
