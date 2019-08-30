package com.radixdlt.tempo.store;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;

import java.util.List;

public interface CommitmentStore extends Store {
	void put(EUID nid, long logicalClock, Hash commitment);

	void put(EUID nid, List<Hash> commitments, long startPosition);

	ImmutableList<Hash> getNext(EUID nid, long logicalClock, int limit);

	ImmutableList<Hash> getLast(EUID nid, int limit);

	void delete(EUID nid);
}
