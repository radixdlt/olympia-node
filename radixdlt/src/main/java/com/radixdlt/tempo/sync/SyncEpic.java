package com.radixdlt.tempo.sync;

import java.util.stream.Stream;

public interface SyncEpic {
	Stream<SyncAction> epic(SyncAction action);

	default Stream<SyncAction> initialActions() {
		return Stream.empty();
	}
}
