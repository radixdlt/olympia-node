package com.radixdlt.tempo.store;

import com.radixdlt.common.EUID;

import java.util.Optional;

public interface LCCursorStore extends Store {
	void put(EUID nid, long cursor);

	Optional<Long> get(EUID nid);
}
