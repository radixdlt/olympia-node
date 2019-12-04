package com.radixdlt.store;

import com.radixdlt.common.EUID;

import java.util.Optional;

public interface CursorStore {
	void put(EUID nid, long cursor);

	Optional<Long> get(EUID nid);

	void reset();

	void close();
}
