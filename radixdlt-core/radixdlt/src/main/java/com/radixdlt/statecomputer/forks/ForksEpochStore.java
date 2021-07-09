package com.radixdlt.statecomputer.forks;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;

public interface ForksEpochStore {
	ImmutableMap<Long, HashCode> getEpochsForkHashes();
	void storeEpochForkHash(long epoch, HashCode forkHash);

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
		};
	}
}
