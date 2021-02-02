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

package com.radixdlt.store;

import com.google.common.collect.Lists;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

public class LedgerEntryGenerator {
	private final Random random = new Random(); // SecureRandom not required for test
	private long stateVersion = 0;
	private long proofVersion = 0;

	public List<LedgerEntry> createLedgerEntries(int n) {
		return createLedgerEntries(n, false);
	}

	public List<LedgerEntry> createLedgerEntriesBatch(int n) {
		return createLedgerEntries(n, true);
	}

	private List<LedgerEntry> createLedgerEntries(int n, boolean sameCommit) {
		// Super paranoid way of doing things
		final var ledgerEntries = new LinkedHashMap<AID, LedgerEntry>(n);
		while (ledgerEntries.size() < n) {
			final var ledgerEntry = createLedgerEntry();
			if (ledgerEntries.put(ledgerEntry.getAID(), ledgerEntry) == null && !sameCommit) {
				this.proofVersion += 1;
			}
		}
		if (sameCommit) {
			this.proofVersion += 1;
		}

		// Make sure return list is ordered by state version.
		return Lists.newArrayList(ledgerEntries.values());
	}

	private LedgerEntry createLedgerEntry() {
		final var pKey = new byte[32];
		this.random.nextBytes(pKey);
		final var atom = new Atom();

		return new LedgerEntry(
			DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.API),
			this.stateVersion++,
			this.proofVersion,
			AID.from(pKey)
		);
	}
}
