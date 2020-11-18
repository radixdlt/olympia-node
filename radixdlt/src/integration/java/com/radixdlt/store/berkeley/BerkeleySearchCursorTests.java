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

package com.radixdlt.store.berkeley;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import org.junit.Assert;
import org.junit.Test;
import org.radix.integration.RadixTestWithStores;

import java.util.List;

public class BerkeleySearchCursorTests extends RadixTestWithStores {
	private static final byte PREFIX = 7; // test value with no special significance

	private LedgerEntryGenerator ledgerEntryGenerator = new LedgerEntryGenerator();

	@Test
	public void store_single_atom__search_by_unique_aid_and_get() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 1);
		StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes());
		getStore().commit(ledgerEntries.get(0), ImmutableSet.of(uniqueIndex), ImmutableSet.of());

		SearchCursor cursor = getStore().search(
			StoreIndex.LedgerIndexType.UNIQUE,
			new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes()),
			LedgerSearchMode.EXACT
		);

		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(0).getAID(), cursor.get());
	}

	@Test
	public void create_two_atoms__store_single_atom__search_by_non_existing_unique_aid__fail() throws Exception {
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(2);
		StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes());
		getStore().commit(ledgerEntries.get(0), ImmutableSet.of(uniqueIndex), ImmutableSet.of());

		SearchCursor cursor = getStore().search(
			StoreIndex.LedgerIndexType.UNIQUE,
			new StoreIndex(PREFIX, ledgerEntries.get(1).getAID().getBytes()),
			LedgerSearchMode.EXACT
		);
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__do_get_and_next() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();

		StoreIndex index = new StoreIndex(PREFIX, identity.euid().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
			getStore().commit(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
		}

		SearchCursor cursor = getStore().search(StoreIndex.LedgerIndexType.DUPLICATE, index, LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(0).getAID(), cursor.get());

		cursor = cursor.next();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(1).getAID(), cursor.get());

		cursor = cursor.next();
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__get_last() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();

		StoreIndex index = new StoreIndex(PREFIX, identity.euid().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
			getStore().commit(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
		}

		SearchCursor cursor = getStore().search(StoreIndex.LedgerIndexType.DUPLICATE, index, LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(0).getAID(), cursor.get());

		cursor = cursor.last();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(1).getAID(), cursor.get());

		cursor = cursor.next();
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__get_next__get_first() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();

		StoreIndex index = new StoreIndex(PREFIX, identity.euid().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
			getStore().commit(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
			getStore().commit(ledgerEntry.getAID());
		}

		SearchCursor cursor = getStore().search(StoreIndex.LedgerIndexType.DUPLICATE, index, LedgerSearchMode.EXACT);
		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(0).getAID(), cursor.get());

		cursor = cursor.next();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(1).getAID(), cursor.get());

		cursor = cursor.first();
		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(0).getAID(), cursor.get());

		cursor = cursor.previous();
		Assert.assertNull(cursor);
	}
}
