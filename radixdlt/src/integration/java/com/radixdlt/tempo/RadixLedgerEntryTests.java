package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.LedgerEntry;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;

import java.util.List;

import static org.junit.Assume.assumeTrue;

public class RadixLedgerEntryTests extends RadixTestWithStores {
	private LedgerEntryGenerator ledgerEntryGenerator = new LedgerEntryGenerator();

	@BeforeClass
	public static void checkTempoAvailable() {
		assumeTrue("Tempo 2.0 must be available", Modules.isAvailable(Tempo.class));
	}

	@Test
	public void store_atom() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 1);
		Tempo tempo = Modules.get(Tempo.class);
		tempo.store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of());
		LedgerEntry actual = tempo.get(ledgerEntries.get(0).getAID()).get();
		Assert.assertEquals(ledgerEntries.get(0), actual);

		// TODO should check LocalSystem clocks once implemented
	}

	@Test
	public void store_duplicate_atom() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 1);
		Modules.get(Tempo.class).store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of());
		Modules.get(Tempo.class).store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of());
	}

	@Test
	public void store_atom__replace_atom__get_replacement__get_original() throws Exception {
		ECKeyPair identity = new ECKeyPair();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		Modules.get(Tempo.class).store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of());
		Assert.assertEquals(ledgerEntries.get(0), Modules.get(Tempo.class).get(ledgerEntries.get(0).getAID()).get());

		Modules.get(Tempo.class).replace(ImmutableSet.of(ledgerEntries.get(0).getAID()), ledgerEntries.get(1), ImmutableSet.of(), ImmutableSet.of());

		Assert.assertTrue("New ledgerEntries is present", Modules.get(Tempo.class).get(ledgerEntries.get(1).getAID()).isPresent());
		Assert.assertFalse("Replaced ledgerEntries is no longer present", Modules.get(Tempo.class).get(ledgerEntries.get(0).getAID()).isPresent());

		// TODO should check LocalSystem clocks once implemented
	}

}
