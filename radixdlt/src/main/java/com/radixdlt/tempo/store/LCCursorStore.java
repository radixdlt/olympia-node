package com.radixdlt.tempo.store;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.Resource;

import java.util.Optional;

public interface LCCursorStore {
	void put(EUID nid, long cursor);

	Optional<Long> get(EUID nid);

	void reset();

	void close();
}
