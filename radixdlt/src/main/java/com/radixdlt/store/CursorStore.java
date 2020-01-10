package com.radixdlt.store;

import com.radixdlt.common.EUID;

import java.util.OptionalLong;

public interface CursorStore {
	void put(EUID nid, long cursor);

	OptionalLong get(EUID nid);

	void reset();

	void close();
}
