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

package com.radixdlt.store.mvstore;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryGenerator;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.Transaction;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MVStoreSearchCursorTests extends RadixTestWithMVStoreStores {
	private static final byte PREFIX = 7; // test value with no special significance
	private static final LedgerEntryGenerator GENERATOR = new LedgerEntryGenerator();

	private LedgerEntryStore createStore() {
		var store = new MVStoreLedgerEntryStore(getSerialization(), getDbEnv(), getSystemCounters());
		store.reset();
		return store;
	}

	@Test
	public void store_single_atom__search_by_unique_aid_and_get() {
		List<LedgerEntry> ledgerEntries = GENERATOR.createLedgerEntries(1);
		StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes());
		LedgerEntryStore ledgerEntryStore = createStore();
		Transaction tx = ledgerEntryStore.createTransaction();

		try {
			var result = ledgerEntryStore.store(tx, ledgerEntries.get(0), ImmutableSet.of(uniqueIndex), ImmutableSet.of());
			assertTrue(result.isSuccess());
			tx.commit();
		} catch (Exception e) {
			tx.abort();
			throw e;
		}

		SearchCursor cursor = ledgerEntryStore.search(
			StoreIndex.LedgerIndexType.UNIQUE,
			new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes())
		);

		assertNotNull(cursor);
		assertEquals(ledgerEntries.get(0).getAID(), cursor.get());
	}

	@Test
	public void create_two_atoms__store_single_atom__search_by_non_existing_unique_aid__fail() throws Exception {
		List<LedgerEntry> ledgerEntries = GENERATOR.createLedgerEntries(2);
		StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes());
		LedgerEntryStore ledgerEntryStore = createStore();
		Transaction tx = ledgerEntryStore.createTransaction();

		try {
			var result = ledgerEntryStore.store(tx, ledgerEntries.get(0), ImmutableSet.of(uniqueIndex), ImmutableSet.of());
			assertTrue(result.isSuccess());
			tx.commit();
		} catch (Exception e) {
			tx.abort();
			throw e;
		}

		SearchCursor cursor = ledgerEntryStore.search(
			StoreIndex.LedgerIndexType.UNIQUE,
			new StoreIndex(PREFIX, ledgerEntries.get(1).getAID().getBytes())
		);
		assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__do_get_and_next() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();
		StoreIndex index = new StoreIndex(PREFIX, identity.euid().toByteArray());
		List<LedgerEntry> ledgerEntries = GENERATOR.createLedgerEntries(2);
		LedgerEntryStore ledgerEntryStore = createStore();
		Transaction tx = ledgerEntryStore.createTransaction();

		try {
			for (LedgerEntry ledgerEntry : ledgerEntries) {
				StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
				var result = ledgerEntryStore.store(tx, ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
				assertTrue(result.isSuccess());
			}
			tx.commit();
		} catch (Exception e) {
			tx.abort();
			throw e;
		}

		SearchCursor cursor = ledgerEntryStore.search(StoreIndex.LedgerIndexType.DUPLICATE, index);
		assertNotNull(cursor);
		assertEquals(ledgerEntries.get(0).getAID(), cursor.get());

		cursor = cursor.next();
		assertNotNull(cursor);
		assertEquals(ledgerEntries.get(1).getAID(), cursor.get());

		cursor = cursor.next();
		assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__get_last() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();
		StoreIndex index = new StoreIndex(PREFIX, identity.euid().toByteArray());
		List<LedgerEntry> ledgerEntries = GENERATOR.createLedgerEntries(2);
		LedgerEntryStore ledgerEntryStore = createStore();
		Transaction tx = ledgerEntryStore.createTransaction();

		try {
			for (LedgerEntry ledgerEntry : ledgerEntries) {
				StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
				var result = ledgerEntryStore.store(tx, ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
				assertTrue(result.isSuccess());
			}
			tx.commit();
		} catch (Exception e) {
			tx.abort();
			throw e;
		}

		SearchCursor cursor = ledgerEntryStore.search(StoreIndex.LedgerIndexType.DUPLICATE, index);
		assertNotNull(cursor);
		assertEquals(ledgerEntries.get(0).getAID(), cursor.get());

		cursor = cursor.last();
		assertNotNull(cursor);
		assertEquals(ledgerEntries.get(1).getAID(), cursor.get());

		cursor = cursor.next();
		assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__get_next__get_first() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();
		StoreIndex index = new StoreIndex(PREFIX, identity.euid().toByteArray());
		List<LedgerEntry> ledgerEntries = GENERATOR.createLedgerEntries(2);
		LedgerEntryStore ledgerEntryStore = createStore();
		Transaction tx = ledgerEntryStore.createTransaction();

		try {
			for (LedgerEntry ledgerEntry : ledgerEntries) {
				StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
				var result = ledgerEntryStore.store(tx, ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
				assertTrue(result.isSuccess());
			}
			tx.commit();
		} catch (Exception e) {
			tx.abort();
			throw e;
		}

		SearchCursor cursor = ledgerEntryStore.search(StoreIndex.LedgerIndexType.DUPLICATE, index);
		assertNotNull(cursor);
		assertEquals(ledgerEntries.get(0).getAID(), cursor.get());

		cursor = cursor.next();
		assertNotNull(cursor);
		assertEquals(ledgerEntries.get(1).getAID(), cursor.get());

		cursor = cursor.first();
		assertNotNull(cursor);
		assertEquals(ledgerEntries.get(0).getAID(), cursor.get());

		cursor = cursor.previous();
		assertNull(cursor);
	}
}
