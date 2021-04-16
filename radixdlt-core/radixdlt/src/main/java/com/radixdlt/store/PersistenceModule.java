/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.store;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.store.berkeley.SerializedVertexStoreState;
import com.radixdlt.store.berkeley.BerkeleySafetyStateStore;

import java.util.Optional;

/**
 * Module which manages persistent storage
 */
public class PersistenceModule extends AbstractModule {
	@Override
	protected void configure() {
		// TODO: should be singletons?
		bind(AtomIndex.class).to(BerkeleyLedgerEntryStore.class).in(Scopes.SINGLETON);
		bind(ImmutableIndex.class).to(BerkeleyLedgerEntryStore.class).in(Scopes.SINGLETON);
		bind(PersistentVertexStore.class).to(BerkeleyLedgerEntryStore.class);
		bind(PersistentSafetyStateStore.class).to(BerkeleySafetyStateStore.class);
		bind(BerkeleySafetyStateStore.class).in(Scopes.SINGLETON);
		bind(DatabaseEnvironment.class).in(Scopes.SINGLETON);
	}

	@Provides
	Optional<SerializedVertexStoreState> serializedVertexStoreState(BerkeleyLedgerEntryStore store) {
		return store.loadLastVertexStoreState();
	}

	@Provides
	StoreConfig storeConfig() {
		return new StoreConfig(1000);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	public EventProcessor<BFTHighQCUpdate> persistQC(
		PersistentVertexStore persistentVertexStore,
		SystemCounters systemCounters
	) {
		return update -> {
			systemCounters.increment(CounterType.PERSISTENCE_VERTEX_STORE_SAVES);
			persistentVertexStore.save(update.getVertexStoreState());
		};
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	public EventProcessor<BFTInsertUpdate> persistUpdates(
		PersistentVertexStore persistentVertexStore,
		SystemCounters systemCounters
	) {
		return update -> {
			systemCounters.increment(CounterType.PERSISTENCE_VERTEX_STORE_SAVES);
			persistentVertexStore.save(update.getVertexStoreState());
		};
	}
}
