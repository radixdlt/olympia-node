package com.radixdlt.tempo.store.berkeley;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.ledger.LedgerEntry;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.utils.Longs;

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
	private ImmutableSet<LedgerIndex> uniqueIndices;

	@JsonProperty("duplicate")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableSet<LedgerIndex> duplicateIndices;

	private LedgerEntryIndices() {
		// For serializer
	}

	private LedgerEntryIndices(ImmutableSet<LedgerIndex> uniqueIndices, ImmutableSet<LedgerIndex> duplicateIndices) {
		this.uniqueIndices = uniqueIndices;
		this.duplicateIndices = duplicateIndices;
	}

	Set<LedgerIndex> getUniqueIndices() {
		return this.uniqueIndices;
	}

	Set<LedgerIndex> getDuplicateIndices() {
		return this.duplicateIndices;
	}

	static LedgerEntryIndices from(LedgerEntry ledgerEntry, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		List<LedgerIndex> offendingIndices = Stream.concat(uniqueIndices.stream(), duplicateIndices.stream())
			.filter(index -> index.getPrefix() == ENTRY_INDEX_PREFIX || index.getPrefix() == SHARD_INDEX_PREFIX)
			.collect(Collectors.toList());
		if (!offendingIndices.isEmpty()) {
			throw new TempoException(String.format(
				"Prefixes %s and %s are reserved for internal use but are used by %s",
				ENTRY_INDEX_PREFIX, SHARD_INDEX_PREFIX, offendingIndices));
		}

		ImmutableSet.Builder<LedgerIndex> allUniqueIndices = ImmutableSet.builder();
		ImmutableSet.Builder<LedgerIndex> allDuplicateIndices = ImmutableSet.builder();

		// add application indices
		allUniqueIndices.addAll(uniqueIndices);
		allDuplicateIndices.addAll(duplicateIndices);

		// add internal indices
		allUniqueIndices.add(new LedgerIndex(ENTRY_INDEX_PREFIX, ledgerEntry.getAID().getBytes()));

		return new LedgerEntryIndices(allUniqueIndices.build(), allDuplicateIndices.build());
	}
}
