package com.radixdlt.tempo.sync.epics;

import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.TempoAtomSynchroniser.ImmediateDispatcher;

import java.util.stream.Stream;

public class IterativeSyncEpic implements SyncEpic {
	private IterativeSyncEpic() {
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Builder() {
		}

		public IterativeSyncEpic build(ImmediateDispatcher dispatcher) {
			return new IterativeSyncEpic();
		}
	}
}
