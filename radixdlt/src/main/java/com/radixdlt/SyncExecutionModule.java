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
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.execution.RadixEngineExecutor;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.SyncedEpochExecutor;
import com.radixdlt.syncer.SyncedEpochExecutor.CommittedStateSyncSender;
import com.radixdlt.syncer.SyncedEpochExecutor.SyncService;
import java.util.Objects;

/**
 * Module which manages synchronized execution
 */
public class SyncExecutionModule extends AbstractModule {

	private final RuntimeProperties runtimeProperties;

	public SyncExecutionModule(RuntimeProperties runtimeProperties) {
		this.runtimeProperties = Objects.requireNonNull(runtimeProperties);
	}

	@Override
	protected void configure() {
		bind(new TypeLiteral<SyncedExecutor<CommittedAtom>>() { }).to(SyncedEpochExecutor.class).in(Scopes.SINGLETON);
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
	private SyncedEpochExecutor syncedEpochExecutor(
		Mempool mempool,
		RadixEngineExecutor executor,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		AddressBookValidatorSetProvider validatorSetProvider,
		SyncService syncService,
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

}
