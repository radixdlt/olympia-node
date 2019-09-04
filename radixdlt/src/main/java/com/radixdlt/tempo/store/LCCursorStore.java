package com.radixdlt.tempo.store;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.Resource;

import java.util.Optional;

public interface LCCursorStore extends Resource {
	void put(EUID nid, long cursor);

	Optional<Long> get(EUID nid);
}
