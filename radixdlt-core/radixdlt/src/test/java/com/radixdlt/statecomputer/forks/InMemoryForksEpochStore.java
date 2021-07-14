/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.statecomputer.forks;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.inject.Singleton;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.engine.RadixEngine.RadixEngineResult;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.statecomputer.LedgerAndBFTProof;

import java.util.TreeMap;

@Singleton
final class InMemoryForksEpochStore implements ForksEpochStore {
	private final Object lock = new Object();
	private final TreeMap<Long, HashCode> epochsForkHashes = new TreeMap<>();

	@SuppressWarnings("unchecked")
	public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return update -> {
			synchronized (lock) {
				final var radixEngineResult = (RadixEngineResult<LedgerAndBFTProof>) update.getStateComputerOutput().get(RadixEngineResult.class);
				if  (radixEngineResult != null) {
					final var nextEpoch = update.getTail().getEpoch() + 1;
					radixEngineResult.getMetadata().getNextForkHash()
						.ifPresent(nextForkHash -> this.epochsForkHashes.put(nextEpoch, nextForkHash));
				}
			}
		};
	}

	@Override
	public ImmutableMap<Long, HashCode> getEpochsForkHashes() {
		synchronized (lock) {
			return ImmutableMap.copyOf(epochsForkHashes);
		}
	}

	@Override
	public void storeEpochForkHash(long epoch, HashCode forkHash) {
		synchronized (lock) {
			this.epochsForkHashes.put(epoch, forkHash);
		}
	}

	@Override
	public CloseableCursor<HashCode> validatorsSystemMetadataCursor(long epoch) {
		return CloseableCursor.empty();
	}
}
