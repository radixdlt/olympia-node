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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.tempo.MemPool;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.ChainedBFT;
import com.radixdlt.delivery.LazyRequestDeliverer;
import com.radixdlt.delivery.LazyRequestDelivererConfiguration;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreView;
import org.junit.After;
import org.junit.Before;
import org.mockito.invocation.InvocationOnMock;
import org.radix.GlobalInjector;
import org.radix.database.DatabaseEnvironment;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.messaging.MessageCentral;

import java.io.IOException;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixTestWithStores extends RadixTest
{
	private DatabaseEnvironment dbEnv;
	private LedgerEntryStore store;
	private ChainedBFT tempo;
	private MessageCentral messageCentral;
	private AddressBook addressBook;

	@Before
	public void beforeEachRadixTest() throws InterruptedException {
		this.dbEnv = new DatabaseEnvironment(getProperties());

		GlobalInjector injector = new GlobalInjector(getProperties(), dbEnv, getLocalSystem(), getUniverse());
		this.messageCentral = injector.getInjector().getInstance(MessageCentral.class);
		this.addressBook = injector.getInjector().getInstance(AddressBook.class);

		MemPool deadApplication = mock(MemPool.class);
		when(deadApplication.takeNextEntry()).then(this::sleepForever);

		store = injector.getInjector().getInstance(LedgerEntryStore.class);
		tempo = new ChainedBFT(deadApplication);
	}

	private <T> T sleepForever(InvocationOnMock invocation) throws InterruptedException {
		Thread.sleep(Long.MAX_VALUE);
		return null;
	}

	@After
	public void afterEachRadixTest() throws IOException {
		// Null checks to better handle case where @Before throws
		if (tempo != null) {
			tempo.close();
		}
		if (store != null) {
			store.close();
			store.reset();
		}
		if (messageCentral != null) {
			messageCentral.close();
		}
		if (addressBook != null) {
			addressBook.close();
		}

		if (this.dbEnv != null) {
			this.dbEnv.stop();
		}

		dbEnv = null;
		store = null;
		tempo = null;
		messageCentral = null;
		addressBook = null;
	}

	protected DatabaseEnvironment getDbEnv() {
		return Objects.requireNonNull(dbEnv, "dbEnv was not initialized");
	}

	protected LedgerEntryStore getStore() {
		return Objects.requireNonNull(store, "store was not initialized");
	}

	protected ChainedBFT getTempo() {
		return Objects.requireNonNull(tempo, "tempo was not initialized");
	}

	public MessageCentral getMessageCentral() {
		return Objects.requireNonNull(messageCentral, "messageCentral was not initialized");
	}
}
