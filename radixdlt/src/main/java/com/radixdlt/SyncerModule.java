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
import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.consensus.AddressBookValidatorSetProvider;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.MempoolNextCommandGenerator;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.SharedMempool;
import com.radixdlt.mempool.SubmissionControl;
import com.radixdlt.mempool.SubmissionControlImpl;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.InternalMessagePasser;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.middleware2.converters.AtomToClientAtomConverter;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.StateSyncNetwork;
import com.radixdlt.syncer.SyncedRadixEngine;
import com.radixdlt.syncer.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.syncer.SyncedRadixEngine.SyncedRadixEngineEventSender;
import java.util.Objects;

public class SyncerModule extends AbstractModule {
	private final RuntimeProperties runtimeProperties;

	public SyncerModule(RuntimeProperties runtimeProperties) {
		this.runtimeProperties = Objects.requireNonNull(runtimeProperties);
	}

	@Override
	protected void configure() {
		// Local messages
		bind(SubmissionControlSender.class).to(InternalMessagePasser.class);
		bind(SubmissionErrorsRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeSender.class).to(InternalMessagePasser.class);
		bind(SyncedRadixEngineEventSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncSender.class).to(InternalMessagePasser.class);

		bind(new TypeLiteral<SyncedStateComputer<CommittedAtom>>() { }).to(SyncedRadixEngine.class).in(Scopes.SINGLETON);
		bind(Mempool.class).to(SharedMempool.class).in(Scopes.SINGLETON);
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
	private SyncedRadixEngine syncedRadixEngine(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		CommittedAtomsStore committedAtomsStore,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		SyncedRadixEngineEventSender syncedRadixEngineEventSender,
		AddressBookValidatorSetProvider validatorSetProvider,
		AddressBook addressBook,
		StateSyncNetwork stateSyncNetwork,
		SystemCounters counters
	) {
		final long viewsPerEpoch = runtimeProperties.get("epochs.views_per_epoch", 100L);
		return new SyncedRadixEngine(
			mempool,
			radixEngine,
			committedAtomsStore,
			committedStateSyncSender,
			epochChangeSender,
			syncedRadixEngineEventSender,
			validatorSetProvider::getValidatorSet,
			View.of(viewsPerEpoch),
			addressBook,
			stateSyncNetwork,
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
}
