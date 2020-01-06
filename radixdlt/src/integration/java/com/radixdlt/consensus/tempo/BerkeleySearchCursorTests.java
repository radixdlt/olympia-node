package com.radixdlt.consensus.tempo;

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
		ECKeyPair identity = new ECKeyPair();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 1);
		StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes());
		getStore().store(ledgerEntries.get(0), ImmutableSet.of(uniqueIndex), ImmutableSet.of());

		SearchCursor cursor = getStore().search(StoreIndex.LedgerIndexType.UNIQUE, new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes()), LedgerSearchMode.EXACT);

		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(0).getAID(), cursor.get());
	}

	@Test
	public void create_two_atoms__store_single_atom__search_by_non_existing_unique_aid__fail() throws Exception {
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(2);
		StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntries.get(0).getAID().getBytes());
		getStore().store(ledgerEntries.get(0), ImmutableSet.of(uniqueIndex), ImmutableSet.of());

		SearchCursor cursor = getStore().search(StoreIndex.LedgerIndexType.UNIQUE, new StoreIndex(PREFIX, ledgerEntries.get(1).getAID().getBytes()), LedgerSearchMode.EXACT);
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__do_get_and_next() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		StoreIndex index = new StoreIndex(PREFIX, identity.getUID().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
			getStore().store(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
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
		ECKeyPair identity = new ECKeyPair();

		StoreIndex index = new StoreIndex(PREFIX, identity.getUID().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
			getStore().store(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
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
		ECKeyPair identity = new ECKeyPair();

		StoreIndex index = new StoreIndex(PREFIX, identity.getUID().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		int logicalClock = 0;
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			StoreIndex uniqueIndex = new StoreIndex(PREFIX, ledgerEntry.getAID().getBytes());
			getStore().store(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
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
