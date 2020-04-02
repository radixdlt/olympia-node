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

package com.radixdlt.consensus.tempo;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;
import org.junit.Assert;
import org.junit.Test;
import org.radix.integration.RadixTestWithStores;

import java.util.List;

public class RadixLedgerEntryTests extends RadixTestWithStores {
	private LedgerEntryGenerator ledgerEntryGenerator = new LedgerEntryGenerator();

	@Test
	public void store_atom() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 1);
		LedgerEntryStore store = getStore();
		store.store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of());
		LedgerEntry actual = store.get(ledgerEntries.get(0).getAID()).get();
		Assert.assertEquals(ledgerEntries.get(0), actual);

		// TODO should check LocalSystem clocks once implemented
	}

	@Test
	public void store_duplicate_atom() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 1);
		getStore().store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of());
		getStore().store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of());
	}

	@Test
	public void store_atom__replace_atom__get_replacement__get_original() throws Exception {
		ECKeyPair identity = ECKeyPair.generateNew();

		List<LedgerEntry> ledgerEntries = ledgerEntryGenerator.createLedgerEntries(identity, 2);
		getStore().store(ledgerEntries.get(0), ImmutableSet.of(), ImmutableSet.of());
		Assert.assertEquals(ledgerEntries.get(0), getStore().get(ledgerEntries.get(0).getAID()).get());

		getStore().replace(ImmutableSet.of(ledgerEntries.get(0).getAID()), ledgerEntries.get(1), ImmutableSet.of(), ImmutableSet.of());

		Assert.assertTrue("New ledgerEntries is present", getStore().get(ledgerEntries.get(1).getAID()).isPresent());
		Assert.assertFalse("Replaced ledgerEntries is no longer present", getStore().get(ledgerEntries.get(0).getAID()).isPresent());

		// TODO should check LocalSystem clocks once implemented
	}

}
