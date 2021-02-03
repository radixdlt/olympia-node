/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.integration;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.store.LedgerEntryStore;
import org.junit.After;
import org.junit.Before;
import com.radixdlt.RadixNodeModule;
import org.radix.database.DatabaseEnvironment;

import java.util.Objects;

public class RadixTestWithStores extends RadixTest {
	@Inject
	private DatabaseEnvironment dbEnv;
	@Inject
	private LedgerEntryStore store;

	@Before
	public void beforeEachRadixTest() {
		Injector injector = Guice.createInjector(new RadixNodeModule(getProperties()));
		injector.injectMembers(this);
	}

	@After
	public void afterEachRadixTest() {
		// Null checks to better handle case where @Before throws
		if (store != null) {
			store.close();
			store.reset();
		}

		if (this.dbEnv != null) {
			this.dbEnv.stop();
		}

		dbEnv = null;
		store = null;
	}

	protected DatabaseEnvironment getDbEnv() {
		return Objects.requireNonNull(dbEnv, "dbEnv was not initialized");
	}

	protected LedgerEntryStore getStore() {
		return Objects.requireNonNull(store, "store was not initialized");
	}
}
