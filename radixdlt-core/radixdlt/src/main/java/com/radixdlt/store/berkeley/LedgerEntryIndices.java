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

package com.radixdlt.store.berkeley;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.LedgerEntry;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SerializerId2("tempo.indices")
public final class LedgerEntryIndices {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	public static final byte ENTRY_INDEX_PREFIX = 0;
	public static final byte SHARD_INDEX_PREFIX = 1;

	@JsonProperty("unique")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableSet<StoreIndex> uniqueIndices;

	@JsonProperty("duplicate")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableSet<StoreIndex> duplicateIndices;

	private LedgerEntryIndices() {
		// For serializer
		this.uniqueIndices = ImmutableSet.of();
		this.duplicateIndices = ImmutableSet.of();
	}

	private LedgerEntryIndices(ImmutableSet<StoreIndex> uniqueIndices, ImmutableSet<StoreIndex> duplicateIndices) {
		this.uniqueIndices = uniqueIndices;
		this.duplicateIndices = duplicateIndices;
	}

	Set<StoreIndex> getUniqueIndices() {
		return this.uniqueIndices;
	}

	Set<StoreIndex> getDuplicateIndices() {
		return this.duplicateIndices;
	}

	static LedgerEntryIndices makeIndices(LedgerEntry ledgerEntry, Set<StoreIndex> uniqueIndices, Set<StoreIndex> duplicateIndices) {
		List<StoreIndex> offendingIndices = Stream.concat(uniqueIndices.stream(), duplicateIndices.stream())
			.filter(index -> index.getPrefix() == ENTRY_INDEX_PREFIX || index.getPrefix() == SHARD_INDEX_PREFIX)
			.collect(Collectors.toList());
		if (!offendingIndices.isEmpty()) {
			throw new BerkeleyStoreException(String.format(
				"Prefixes %s and %s are reserved for internal use but are used by %s",
				ENTRY_INDEX_PREFIX, SHARD_INDEX_PREFIX, offendingIndices));
		}

		ImmutableSet.Builder<StoreIndex> allUniqueIndices = ImmutableSet.builder();
		ImmutableSet.Builder<StoreIndex> allDuplicateIndices = ImmutableSet.builder();

		// add application indices
		allUniqueIndices.addAll(uniqueIndices);
		allDuplicateIndices.addAll(duplicateIndices);

		// add internal indices
		allUniqueIndices.add(new StoreIndex(ENTRY_INDEX_PREFIX, ledgerEntry.getAID().getBytes()));

		return new LedgerEntryIndices(allUniqueIndices.build(), allDuplicateIndices.build());
	}
}
