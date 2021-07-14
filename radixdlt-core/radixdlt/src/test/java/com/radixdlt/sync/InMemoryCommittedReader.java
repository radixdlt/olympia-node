/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.sync;

import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;

import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A correct in memory committed reader used for testing
 */
class InMemoryCommittedReader implements CommittedReader {
	private final Object lock = new Object();
	private final TreeMap<Long, VerifiedTxnsAndProof> commandsAndProof = new TreeMap<>();
	private final LedgerAccumulatorVerifier accumulatorVerifier;
	private final TreeMap<Long, LedgerProof> epochProofs = new TreeMap<>();

	@Inject
	InMemoryCommittedReader(LedgerAccumulatorVerifier accumulatorVerifier) {
		this.accumulatorVerifier = Objects.requireNonNull(accumulatorVerifier);
	}

	@SuppressWarnings("unchecked")
	public EventProcessor<LedgerUpdate> updateProcessor() {
		return update -> {
			synchronized (lock) {
				var commands = update.getNewTxns();
				long firstVersion = update.getTail().getStateVersion() - commands.size() + 1;
				for (long version = firstVersion; version <= update.getTail().getStateVersion(); version++) {
					int index = (int) (version - firstVersion);
					commandsAndProof.put(
						version,
						VerifiedTxnsAndProof.create(
							commands.subList(index, commands.size()),
							update.getTail()
						)
					);
				}

				final var nextEpoch = update.getTail().getEpoch() + 1;

				if (update.getTail().isEndOfEpoch()) {
					this.epochProofs.put(nextEpoch, update.getTail());
				}
			}
		};
	}

	@Override
	public VerifiedTxnsAndProof getNextCommittedTxns(DtoLedgerProof start) {
		synchronized (lock) {
			final long stateVersion = start.getLedgerHeader().getAccumulatorState().getStateVersion();
			Entry<Long, VerifiedTxnsAndProof> entry = commandsAndProof.higherEntry(stateVersion);

			if (entry != null) {
				List<Txn> txns = accumulatorVerifier
					.verifyAndGetExtension(
						start.getLedgerHeader().getAccumulatorState(),
						entry.getValue().getTxns(),
						txn -> txn.getId().asHashCode(),
						entry.getValue().getProof().getAccumulatorState()
					).orElseThrow(() -> new RuntimeException());

				return VerifiedTxnsAndProof.create(txns, entry.getValue().getProof());
			}

			return null;
		}
	}

	@Override
	public Optional<LedgerProof> getEpochProof(long epoch) {
		synchronized (lock) {
			return Optional.ofNullable(epochProofs.get(epoch));
		}
	}

	@Override
	public Optional<LedgerProof> getLastProof() {
		return Optional.ofNullable(commandsAndProof.lastEntry()).map(p -> p.getValue().getProof());
	}
}
