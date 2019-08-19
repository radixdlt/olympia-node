package com.radixdlt.mock;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.ledger.LedgerIndex;

/**
 * Utility methods for indexing {@link MockAtom}s
 */
final class MockAtomIndexer {
	private MockAtomIndexer() {
		throw new IllegalStateException("Can't construct");
	}

	static ImmutableSet<LedgerIndex> getUniqueIndices(MockAtomContent content) {
		return ImmutableSet.of(content.getKey());
	}

	static ImmutableSet<LedgerIndex> getDuplicateIndices(MockAtomContent content) {
		return content.getApplicationIndices();
	}
}
