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

package com.radixdlt.integration.distributed;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.CommittedReader;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A correct in memory committed reader used for testing
 */
class InMemoryCommittedReader implements LedgerUpdateSender, CommittedReader {

	private final TreeMap<Long, VerifiedCommandsAndProof> commandsAndProof = new TreeMap<>();
	private final LedgerAccumulatorVerifier accumulatorVerifier;
	private final Hasher hasher;
	private final TreeMap<Long, VerifiedLedgerHeaderAndProof> epochProofs = new TreeMap<>();

	@Inject
	InMemoryCommittedReader(
		LedgerAccumulatorVerifier accumulatorVerifier,
		Hasher hasher
	) {
		this.accumulatorVerifier = Objects.requireNonNull(accumulatorVerifier);
		this.hasher = Objects.requireNonNull(hasher);
	}

	@Override
	public void sendLedgerUpdate(LedgerUpdate update) {
		long firstVersion = update.getNewCommands().isEmpty() ? update.getTail().getStateVersion()
			: update.getTail().getStateVersion() - update.getNewCommands().size() + 1;
		for (long version = firstVersion; version <= update.getTail().getStateVersion(); version++) {
			commandsAndProof.put(version, new VerifiedCommandsAndProof(update.getNewCommands(), update.getTail()));
		}

		if (update.getTail().isEndOfEpoch()) {
			this.epochProofs.put(update.getTail().getEpoch() + 1, update.getTail());
		}
	}

	@Override
	public VerifiedCommandsAndProof getNextCommittedCommands(DtoLedgerHeaderAndProof start, int batchSize) {
		final long stateVersion = start.getLedgerHeader().getAccumulatorState().getStateVersion();
		Entry<Long, VerifiedCommandsAndProof> entry = commandsAndProof.higherEntry(stateVersion);

		if (entry != null) {
			ImmutableList<Command> cmds = accumulatorVerifier
				.verifyAndGetExtension(
					start.getLedgerHeader().getAccumulatorState(),
					entry.getValue().getCommands(),
					hasher::hash,
					entry.getValue().getHeader().getAccumulatorState()
				).orElseThrow(() -> {
					String msg = String.format("Failed getNextCommittedCommands: start=%s, entry=%s", start, entry);
					return new RuntimeException(msg);
				});

			return new VerifiedCommandsAndProof(cmds, entry.getValue().getHeader());
		}

		return null;
	}

	@Override
	public Optional<VerifiedLedgerHeaderAndProof> getEpochVerifiedHeader(long epoch) {
		return Optional.ofNullable(epochProofs.get(epoch));
	}
}
