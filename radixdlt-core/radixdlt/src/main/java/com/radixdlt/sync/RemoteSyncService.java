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
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
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

import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

import com.radixdlt.network.addressbook.PeersView;
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

	private VerifiedLedgerHeaderAndProof currentHeader;
	private BFTValidatorSet currentValidatorSet;

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
		@LastProof VerifiedLedgerHeaderAndProof initialHeader,
		BFTValidatorSet initialValidatorSet
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
		this.currentValidatorSet = initialValidatorSet;
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

		final var verifiable = new DtoCommandsAndProof(
			committedCommands.getCommands(),
			remoteCurrentHeader,
			committedCommands.getHeader().toDto()
		);

		log.trace("REMOTE_SYNC_REQUEST: Sending response {} to request {} from {}", verifiable, remoteCurrentHeader, sender);

		syncResponseDispatcher.dispatch(sender, SyncResponse.create(verifiable));
	}

	private VerifiedCommandsAndProof getCommittedCommandsForSyncRequest(DtoLedgerHeaderAndProof startHeader) {
		final var start = System.currentTimeMillis();
		final var result = committedReader.getNextCommittedCommands(startHeader);
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
		final VerifiedLedgerHeaderAndProof updatedHeader = ledgerUpdate.getTail();
		if (accComparator.compare(updatedHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) > 0) {
			this.currentHeader = updatedHeader;
			ledgerUpdate.getNextValidatorSet().ifPresent(newValidatorSet -> this.currentValidatorSet = newValidatorSet);
			this.sendStatusUpdateToSomePeers(updatedHeader);
		}
	}

	private void sendStatusUpdateToSomePeers(VerifiedLedgerHeaderAndProof header) {
		if (!(this.localSyncService.getSyncState() instanceof SyncState.IdleState)) {
			return; // not sending any updates if the node is syncing itself
		}

		final var statusUpdate = LedgerStatusUpdate.create(header);

		final var nonValidatorPeers = this.peersView.peers().stream()
			.map(peer -> BFTNode.create(peer.getKey()))
			.filter(not(this.currentValidatorSet::containsNode))
			.collect(Collectors.toList());

		Collections.shuffle(nonValidatorPeers);

		nonValidatorPeers.stream()
			.limit(syncConfig.ledgerStatusUpdateMaxPeersToNotify())
			.forEach(peer -> {
				if (this.ledgerStatusUpdateSendRateLimiter.tryAcquire()) {
					statusUpdateDispatcher.dispatch(peer, statusUpdate);
				}
			});
	}
}
