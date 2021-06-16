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

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoTxnsAndProof;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.ledger.LedgerUpdate;

import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service which serves remote sync requests.
 */
public final class RemoteSyncService {
	private static final Logger log = LogManager.getLogger();

	private final PeersView peersView;
	private final LocalSyncService localSyncService; // TODO: consider removing this dependency
	private final CommittedReader committedReader;
	private final RemoteEventDispatcher<StatusResponse> statusResponseDispatcher;
	private final RemoteEventDispatcher<SyncResponse> syncResponseDispatcher;
	private final RemoteEventDispatcher<LedgerStatusUpdate> statusUpdateDispatcher;
	private final SyncConfig syncConfig;
	private final SystemCounters systemCounters;
	private final Comparator<AccumulatorState> accComparator;
	private final RateLimiter ledgerStatusUpdateSendRateLimiter;

	private LedgerProof currentHeader;

	@Inject
	public RemoteSyncService(
		PeersView peersView,
		LocalSyncService localSyncService,
		CommittedReader committedReader,
		RemoteEventDispatcher<StatusResponse> statusResponseDispatcher,
		RemoteEventDispatcher<SyncResponse> syncResponseDispatcher,
		RemoteEventDispatcher<LedgerStatusUpdate> statusUpdateDispatcher,
		SyncConfig syncConfig,
		SystemCounters systemCounters,
		Comparator<AccumulatorState> accComparator,
		@LastProof LedgerProof initialHeader
	) {
		this.peersView = Objects.requireNonNull(peersView);
		this.localSyncService = Objects.requireNonNull(localSyncService);
		this.committedReader = Objects.requireNonNull(committedReader);
		this.syncConfig = Objects.requireNonNull(syncConfig);
		this.statusResponseDispatcher = Objects.requireNonNull(statusResponseDispatcher);
		this.syncResponseDispatcher = Objects.requireNonNull(syncResponseDispatcher);
		this.statusUpdateDispatcher = Objects.requireNonNull(statusUpdateDispatcher);
		this.systemCounters = systemCounters;
		this.accComparator = Objects.requireNonNull(accComparator);
		this.ledgerStatusUpdateSendRateLimiter = RateLimiter.create(syncConfig.maxLedgerUpdatesRate());

		this.currentHeader = initialHeader;
	}

	public RemoteEventProcessor<SyncRequest> syncRequestEventProcessor() {
		return this::processSyncRequest;
	}

	private void processSyncRequest(BFTNode sender, SyncRequest syncRequest) {
		final var remoteCurrentHeader = syncRequest.getHeader();
		final var committedCommands = getCommittedCommandsForSyncRequest(remoteCurrentHeader);

		if (committedCommands == null) {
			log.warn("REMOTE_SYNC_REQUEST: Unable to serve sync request {} from sender {}.", remoteCurrentHeader, sender);
			return;
		}

		final var verifiable = new DtoTxnsAndProof(
			committedCommands.getTxns().stream().map(Txn::getPayload).collect(Collectors.toList()),
			remoteCurrentHeader,
			committedCommands.getProof().toDto()
		);

		log.trace("REMOTE_SYNC_REQUEST: Sending response {} to request {} from {}", verifiable, remoteCurrentHeader, sender);

		systemCounters.increment(CounterType.SYNC_REMOTE_REQUESTS_PROCESSED);
		syncResponseDispatcher.dispatch(sender, SyncResponse.create(verifiable));
	}

	private VerifiedTxnsAndProof getCommittedCommandsForSyncRequest(DtoLedgerProof startHeader) {
		final var start = System.currentTimeMillis();
		final var result = committedReader.getNextCommittedTxns(startHeader);
		final var finish = System.currentTimeMillis();
		systemCounters.set(CounterType.SYNC_LAST_READ_MILLIS, finish - start);
		return result;
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
		final LedgerProof updatedHeader = ledgerUpdate.getTail();
		if (accComparator.compare(updatedHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) > 0) {
			this.currentHeader = updatedHeader;
			this.sendStatusUpdateToSomePeers(updatedHeader);
		}
	}

	private void sendStatusUpdateToSomePeers(LedgerProof header) {
		if (!(this.localSyncService.getSyncState() instanceof SyncState.IdleState)) {
			return; // not sending any updates if the node is syncing itself
		}

		final var statusUpdate = LedgerStatusUpdate.create(header);

		final var currentPeers = this.peersView.peers().collect(Collectors.toList());
		Collections.shuffle(currentPeers);

		currentPeers.stream()
			.limit(syncConfig.ledgerStatusUpdateMaxPeersToNotify())
			.map(PeersView.PeerInfo::bftNode)
			.forEach(peer -> {
				if (this.ledgerStatusUpdateSendRateLimiter.tryAcquire()) {
					statusUpdateDispatcher.dispatch(peer, statusUpdate);
				}
			});
	}
}
