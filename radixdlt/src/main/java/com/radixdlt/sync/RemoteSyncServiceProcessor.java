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

import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service which serves remote sync requests
 */
public class RemoteSyncServiceProcessor {
	private static final Logger log = LogManager.getLogger();

	private final CommittedReader committedReader;
	private final StateSyncNetwork stateSyncNetwork;

	private final int batchSize;

	public RemoteSyncServiceProcessor(
		CommittedReader committedReader,
		StateSyncNetwork stateSyncNetwork,
		int batchSize
	) {
		if (batchSize <= 0) {
			throw new IllegalArgumentException();
		}
		this.committedReader = Objects.requireNonNull(committedReader);
		this.batchSize = batchSize;
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
	}

	public void processRemoteSyncRequest(RemoteSyncRequest syncRequest) {
		log.info("SYNC_REQUEST: {}", syncRequest);
		DtoLedgerHeaderAndProof currentHeader = syncRequest.getCurrentHeader();
		VerifiedCommandsAndProof committedCommands = committedReader.getNextCommittedCommands(currentHeader, batchSize);
		if (committedCommands == null) {
			return;
		}

		DtoCommandsAndProof verifiable = new DtoCommandsAndProof(
			committedCommands.getCommands(),
			currentHeader,
			committedCommands.getHeader().toDto()
		);

		stateSyncNetwork.sendSyncResponse(syncRequest.getNode(), verifiable);
	}
}
