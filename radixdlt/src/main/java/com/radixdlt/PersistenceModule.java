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
import com.google.inject.Singleton;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.store.CursorStore;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreView;
import com.radixdlt.store.berkeley.BerkeleyCursorStore;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import org.radix.database.DatabaseEnvironment;

/**
 * Module which manages persistent storage
 */
public class PersistenceModule extends AbstractModule {
	@Override
	protected void configure() {
		// TODO: should be singletons?
		bind(LedgerEntryStore.class).to(BerkeleyLedgerEntryStore.class);
		bind(LedgerEntryStoreView.class).to(BerkeleyLedgerEntryStore.class);
		bind(CursorStore.class).to(BerkeleyCursorStore.class);
	}

	@Provides
	@Singleton
	private DatabaseEnvironment databaseEnvironment(RuntimeProperties properties) {
		return new DatabaseEnvironment(properties);
	}
}
