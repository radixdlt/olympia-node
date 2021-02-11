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

package com.radixdlt.sync;

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.ledger.LedgerUpdate;

import java.util.Comparator;
import java.util.Objects;

import com.radixdlt.store.NextCommittedLimitReachedException;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service which serves remote sync requests.
 */
public final class RemoteSyncService {
	private static final Logger log = LogManager.getLogger();

	private final CommittedReader committedReader;
	private final RemoteEventDispatcher<StatusResponse> statusResponseDispatcher;
	private final RemoteEventDispatcher<SyncResponse> syncResponseDispatcher;
	private final SyncConfig syncConfig;
	private final SystemCounters systemCounters;
	private final Comparator<AccumulatorState> accComparator;

	private VerifiedLedgerHeaderAndProof currentHeader;

	public RemoteSyncService(
		CommittedReader committedReader,
		RemoteEventDispatcher<StatusResponse> statusResponseDispatcher,
		RemoteEventDispatcher<SyncResponse> syncResponseDispatcher,
		SyncConfig syncConfig,
		SystemCounters systemCounters,
		Comparator<AccumulatorState> accComparator,
		VerifiedLedgerHeaderAndProof initialHeader
	) {
		this.committedReader = Objects.requireNonNull(committedReader);
		this.syncConfig = Objects.requireNonNull(syncConfig);
		this.statusResponseDispatcher = Objects.requireNonNull(statusResponseDispatcher);
		this.syncResponseDispatcher = Objects.requireNonNull(syncResponseDispatcher);
		this.systemCounters = systemCounters;
		this.accComparator = Objects.requireNonNull(accComparator);

		this.currentHeader = initialHeader;
	}

	public RemoteEventProcessor<SyncRequest> syncRequestEventProcessor() {
		return this::processSyncRequest;
	}

	private void processSyncRequest(BFTNode sender, SyncRequest syncRequest) {
		final DtoLedgerHeaderAndProof remoteCurrentHeader = syncRequest.getHeader();
		final VerifiedCommandsAndProof committedCommands;
		try {
			final long start = System.currentTimeMillis();
			committedCommands = committedReader.getNextCommittedCommands(
				remoteCurrentHeader,
				syncConfig.responseBatchSize()
			);
			final long finish = System.currentTimeMillis();
			systemCounters.set(CounterType.SYNC_LAST_READ_MILLIS, finish - start);
		} catch (NextCommittedLimitReachedException e) {
			log.warn("REMOTE_SYNC_REQUEST: Unable to serve sync request {}.", remoteCurrentHeader);
			return;
		}

		if (committedCommands == null) {
			log.warn("REMOTE_SYNC_REQUEST: Unable to serve sync request {} from sender {}.", remoteCurrentHeader, sender);
			return;
		}

		DtoCommandsAndProof verifiable = new DtoCommandsAndProof(
			committedCommands.getCommands(),
			remoteCurrentHeader,
			committedCommands.getHeader().toDto()
		);

		log.info("REMOTE_SYNC_REQUEST: Sending response {} to request {} from {}", verifiable, remoteCurrentHeader, sender);

		syncResponseDispatcher.dispatch(sender, SyncResponse.create(verifiable));
	}

	public RemoteEventProcessor<StatusRequest> statusRequestEventProcessor() {
		return this::processStatusRequest;
	}

	private void processStatusRequest(BFTNode sender, StatusRequest statusRequest) {
		statusResponseDispatcher.dispatch(sender, StatusResponse.create(this.currentHeader));
	}

	public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return this::processLedgerUpdate;
	}

	private void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		final VerifiedLedgerHeaderAndProof updatedHeader = ledgerUpdate.getTail();
		if (accComparator.compare(updatedHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) > 0) {
			this.currentHeader = updatedHeader;
		}
	}
}
