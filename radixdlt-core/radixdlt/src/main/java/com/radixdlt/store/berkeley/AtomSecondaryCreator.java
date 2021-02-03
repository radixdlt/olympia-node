/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.store.berkeley;

import com.radixdlt.identifiers.AID;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryMultiKeyCreator;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

class AtomSecondaryCreator implements SecondaryMultiKeyCreator {
	private final Function<DatabaseEntry, Set<StoreIndex>> indexer;

	private AtomSecondaryCreator(Function<DatabaseEntry, Set<StoreIndex>> indexer) {
		this.indexer = Objects.requireNonNull(indexer, "indexer is required");
	}

	@Override
	public void createSecondaryKeys(
		SecondaryDatabase database,
		DatabaseEntry key,
		DatabaseEntry value,
		Set<DatabaseEntry> secondaries
	) {
		// key should be primary key where first 8 bytes is the long clock
		Set<StoreIndex> indices = indexer.apply(key);
		indices.forEach(index -> secondaries.add(BerkeleyLedgerEntryStore.entry(index.asKey())));
	}

	public static AtomSecondaryCreator creator(
		Map<AID, LedgerEntryIndices> atomIndices,
		Function<LedgerEntryIndices, Set<StoreIndex>> indexer
	) {
		return new AtomSecondaryCreator(
			key -> {
				var ledgerEntryIndices = atomIndices.get(BerkeleyLedgerEntryStore.getAidFromPKey(key));

				if (ledgerEntryIndices == null) {
					throw new IllegalStateException(
						"Indices for atom '" + Longs.fromByteArray(key.getData()) + "' not available"
					);
				}
				return indexer.apply(ledgerEntryIndices);
			}
		);
	}
}
