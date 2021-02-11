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

package com.radixdlt.store;

import com.radixdlt.store.berkeley.SerializedVertexStoreState;

import java.util.Optional;
import java.util.Set;

/**
 * A read/write instance of a ledger store containing ledger entries.
 */
public interface LedgerEntryStore extends LedgerEntryStoreView {
	Transaction createTransaction();

	LedgerEntryStoreResult store(
		Transaction tx,
		LedgerEntry atom,
		Set<StoreIndex> uniqueIndices,
		Set<StoreIndex> duplicateIndices
	);

	void reset();

	void close();

	Optional<SerializedVertexStoreState> loadLastVertexStoreState();
}
