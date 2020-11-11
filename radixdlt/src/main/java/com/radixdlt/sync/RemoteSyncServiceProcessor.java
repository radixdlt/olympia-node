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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.store.berkeley.NextCommittedLimitReachedException;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service which serves remote sync requests
 */
public class RemoteSyncServiceProcessor implements RemoteEventProcessor<DtoLedgerHeaderAndProof> {
	private static final Logger log = LogManager.getLogger();

	private final CommittedReader committedReader;
	private final StateSyncNetworkSender stateSyncNetwork;

	private final int batchSize;

	public RemoteSyncServiceProcessor(
		CommittedReader committedReader,
		StateSyncNetworkSender stateSyncNetwork,
		int batchSize
	) {
		if (batchSize <= 0) {
			throw new IllegalArgumentException();
		}
		this.committedReader = Objects.requireNonNull(committedReader);
		this.batchSize = batchSize;
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
	}

	@Override
	public void process(BFTNode sender, DtoLedgerHeaderAndProof currentHeader) {
		if (currentHeader.getLedgerHeader().isEndOfEpoch()) {
			log.info("REMOTE_EPOCH_SYNC_REQUEST: {} {}", sender, currentHeader);
			long currentEpoch = currentHeader.getLedgerHeader().getEpoch() + 1;
			long nextEpoch = currentEpoch + 1;
			Optional<VerifiedLedgerHeaderAndProof> nextEpochProof = committedReader.getEpochVerifiedHeader(nextEpoch);
			if (nextEpochProof.isEmpty()) {
				log.warn("REMOTE_EPOCH_SYNC_REQUEST: Unable to serve epoch sync request {} {}", sender, currentHeader);
				return;
			}

			DtoCommandsAndProof dtoCommandsAndProof = new DtoCommandsAndProof(
				ImmutableList.of(),
				currentHeader, nextEpochProof.get().toDto()
			);
			log.info("REMOTE_EPOCH_SYNC_REQUEST: Sending response {}", dtoCommandsAndProof);
			stateSyncNetwork.sendSyncResponse(sender, dtoCommandsAndProof);
			return;
		}

		final VerifiedCommandsAndProof committedCommands;
		try {
			committedCommands = committedReader.getNextCommittedCommands(currentHeader, batchSize);
		} catch (NextCommittedLimitReachedException e) {
			log.warn("REMOTE_SYNC_REQUEST: Unable to serve sync request {}.", currentHeader);
			return;
		}

		if (committedCommands == null) {
			log.warn("REMOTE_SYNC_REQUEST: Unable to serve sync request {}.", currentHeader);
			return;
		}

		DtoCommandsAndProof verifiable = new DtoCommandsAndProof(
			committedCommands.getCommands(),
			currentHeader,
			committedCommands.getHeader().toDto()
		);

		log.info("REMOTE_SYNC_REQUEST: Sending response {}", verifiable);

		stateSyncNetwork.sendSyncResponse(sender, verifiable);
	}
}
