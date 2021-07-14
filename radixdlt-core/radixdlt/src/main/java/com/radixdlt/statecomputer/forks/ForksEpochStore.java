package com.radixdlt.statecomputer.forks;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.atom.CloseableCursor;

public interface ForksEpochStore {
	ImmutableMap<Long, HashCode> getEpochsForkHashes();
	void storeEpochForkHash(long epoch, HashCode forkHash);
	CloseableCursor<HashCode> validatorsSystemMetadataCursor(long epoch);

	static ForksEpochStore mocked() {
		return new ForksEpochStore() {
			@Override
			public ImmutableMap<Long, HashCode> getEpochsForkHashes() {
				return ImmutableMap.of();
			}

			@Override
			public void storeEpochForkHash(long epoch, HashCode forkHash) {
				// no-op
			}

			@Override
			public CloseableCursor<HashCode> validatorsSystemMetadataCursor(long epoch) {
				return CloseableCursor.empty();
			}
		};
	}
}
