package com.radixdlt.tempo.store;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.common.Pair;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.tempo.AtomStore;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.sync.IterativeCursor;
import org.radix.shards.ShardSpace;

import java.util.Objects;
import java.util.Optional;

class AtomStoreViewAdapter implements AtomStoreView {
	private final AtomStore store;

	AtomStoreViewAdapter(AtomStore store) {
		this.store = Objects.requireNonNull(store, "store is required");
	}

	@Override
	public boolean contains(AID aid) {
		return store.contains(aid);
	}

	@Override
	public Optional<TempoAtom> get(AID aid) {
		return store.get(aid);
	}

	@Override
	public LedgerCursor search(LedgerCursor.Type type, LedgerIndex index, LedgerSearchMode mode) {
		return store.search(type, index, mode);
	}

	@Override
	public Pair<ImmutableList<AID>, IterativeCursor> getNext(IterativeCursor cursor, int limit, ShardSpace shardSpace) {
		return store.getNext(cursor, limit, shardSpace);
	}
}
