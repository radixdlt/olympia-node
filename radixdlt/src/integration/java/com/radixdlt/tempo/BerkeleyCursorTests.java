package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.ledger.LedgerEntry;
import com.radixdlt.tempo.store.LedgerEntryStore;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;

import java.util.List;

import static com.radixdlt.tempo.store.berkeley.LedgerEntryIndices.ENTRY_INDEX_PREFIX;
import static org.junit.Assume.assumeTrue;

public class BerkeleyCursorTests extends RadixTestWithStores {

	private LedgerEntryGenerator ledgerEntryGenerator = new LedgerEntryGenerator();

	@BeforeClass
	public static void checkTempoAvailable() {
		assumeTrue(Modules.isAvailable(Tempo.class));
	}

	@Test
	public void store_single_atom__search_by_unique_aid_and_get() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 1);
		LedgerIndex uniqueIndex = new LedgerIndex(ENTRY_INDEX_PREFIX, ledgerEntries.get(0).getAID().getBytes());
		Modules.get(Tempo.class).store(ledgerEntries.get(0), ImmutableSet.of(uniqueIndex), ImmutableSet.of());

		LedgerCursor cursor = Modules.get(Tempo.class).search(LedgerIndex.LedgerIndexType.UNIQUE, new LedgerIndex(ENTRY_INDEX_PREFIX, ledgerEntries.get(0).getAID().getBytes()), LedgerSearchMode.EXACT);

		Assert.assertNotNull(cursor);
		Assert.assertEquals(ledgerEntries.get(0).getAID(), cursor.get());
	}

	@Test
	public void create_two_atoms__store_single_atom__search_by_non_existing_unique_aid__fail() throws Exception {
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(2);
		LedgerIndex uniqueIndex = new LedgerIndex(ENTRY_INDEX_PREFIX, ledgerEntries.get(0).getAID().getBytes());
		Modules.get(Tempo.class).store(ledgerEntries.get(0), ImmutableSet.of(uniqueIndex), ImmutableSet.of());

		LedgerCursor cursor = Modules.get(Tempo.class).search(LedgerIndex.LedgerIndexType.UNIQUE, new LedgerIndex(ENTRY_INDEX_PREFIX, ledgerEntries.get(1).getAID().getBytes()), LedgerSearchMode.EXACT);
		Assert.assertNull(cursor);
	}

	@Test
	public void create_and_store_two_atoms__search_by_index__do_get_and_next() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		LedgerIndex index = new LedgerIndex(ENTRY_INDEX_PREFIX, identity.getUID().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			LedgerIndex uniqueIndex = new LedgerIndex(ENTRY_INDEX_PREFIX, ledgerEntry.getAID().getBytes());
			Modules.get(Tempo.class).store(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
		}

		LedgerCursor cursor = Modules.get(Tempo.class).search(LedgerIndex.LedgerIndexType.DUPLICATE, index, LedgerSearchMode.EXACT);
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

		LedgerIndex index = new LedgerIndex(ENTRY_INDEX_PREFIX, identity.getUID().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			LedgerIndex uniqueIndex = new LedgerIndex(ENTRY_INDEX_PREFIX, ledgerEntry.getAID().getBytes());
			Modules.get(Tempo.class).store(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
		}

		LedgerCursor cursor = Modules.get(Tempo.class).search(LedgerIndex.LedgerIndexType.DUPLICATE, index, LedgerSearchMode.EXACT);
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

		LedgerIndex index = new LedgerIndex(ENTRY_INDEX_PREFIX, identity.getUID().toByteArray());
		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		int logicalClock = 0;
		for (LedgerEntry ledgerEntry : ledgerEntries) {
			LedgerIndex uniqueIndex = new LedgerIndex(ENTRY_INDEX_PREFIX, ledgerEntry.getAID().getBytes());
			Modules.get(Tempo.class).store(ledgerEntry, ImmutableSet.of(uniqueIndex), ImmutableSet.of(index));
			Modules.get(LedgerEntryStore.class).commit(ledgerEntry.getAID(), logicalClock++);
		}

		LedgerCursor cursor = Modules.get(Tempo.class).search(LedgerIndex.LedgerIndexType.DUPLICATE, index, LedgerSearchMode.EXACT);
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
