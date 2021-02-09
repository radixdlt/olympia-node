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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.network.addressbook.AddressBook;

import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;

import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import com.radixdlt.sync.validation.RemoteSyncResponseSignaturesVerifier;
import com.radixdlt.sync.validation.RemoteSyncResponseValidatorSetVerifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processes sync service messages and manages ledger sync state machine.
 * Thread-safety must be handled by caller.
 */
@NotThreadSafe
public final class LocalSyncService {

	public interface VerifiedSyncResponseSender {
		void sendVerifiedSyncResponse(SyncResponse remoteSyncResponse);
	}

	public interface InvalidSyncResponseSender {
		void sendInvalidSyncResponse(SyncResponse remoteSyncResponse);
	}

	private static final Logger log = LogManager.getLogger();

	private final ScheduledEventDispatcher<SyncCheckTrigger> syncCheckTriggerDispatcher;
	private final RemoteEventDispatcher<StatusRequest> statusRequestDispatcher;
	private final ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutDispatcher;
	private final RemoteEventDispatcher<SyncRequest> syncRequestDispatcher;
	private final ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher;
	private final SyncConfig syncConfig;
	private final SystemCounters systemCounters;
	private final AddressBook addressBook;
	private final Comparator<AccumulatorState> accComparator;
	private final Hasher hasher;
	private final RemoteSyncResponseValidatorSetVerifier validatorSetVerifier;
	private final RemoteSyncResponseSignaturesVerifier signaturesVerifier;
	private final LedgerAccumulatorVerifier accumulatorVerifier;
	private final VerifiedSyncResponseSender verifiedSender;
	private final InvalidSyncResponseSender invalidSyncedCommandsSender;

	private SyncState syncState;

	@Inject
	public LocalSyncService(
		ScheduledEventDispatcher<SyncCheckTrigger> syncCheckTriggerDispatcher,
		RemoteEventDispatcher<StatusRequest> statusRequestDispatcher,
		ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutDispatcher,
		RemoteEventDispatcher<SyncRequest> syncRequestDispatcher,
		ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher,
		SyncConfig syncConfig,
		SystemCounters systemCounters,
		AddressBook addressBook,
		Comparator<AccumulatorState> accComparator,
		Hasher hasher,
		RemoteSyncResponseValidatorSetVerifier validatorSetVerifier,
		RemoteSyncResponseSignaturesVerifier signaturesVerifier,
		LedgerAccumulatorVerifier accumulatorVerifier,
		VerifiedSyncResponseSender verifiedSender,
		InvalidSyncResponseSender invalidSyncedCommandsSender,
		SyncState initialState
	) {
		this.syncCheckTriggerDispatcher = Objects.requireNonNull(syncCheckTriggerDispatcher);
		this.statusRequestDispatcher = Objects.requireNonNull(statusRequestDispatcher);
		this.syncCheckReceiveStatusTimeoutDispatcher = Objects.requireNonNull(syncCheckReceiveStatusTimeoutDispatcher);
		this.syncRequestDispatcher = Objects.requireNonNull(syncRequestDispatcher);
		this.syncRequestTimeoutDispatcher = Objects.requireNonNull(syncRequestTimeoutDispatcher);
		this.syncConfig = Objects.requireNonNull(syncConfig);
		this.systemCounters = Objects.requireNonNull(systemCounters);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.accComparator = Objects.requireNonNull(accComparator);
		this.hasher = Objects.requireNonNull(hasher);
		this.validatorSetVerifier = Objects.requireNonNull(validatorSetVerifier);
		this.signaturesVerifier = Objects.requireNonNull(signaturesVerifier);
		this.accumulatorVerifier = Objects.requireNonNull(accumulatorVerifier);
		this.verifiedSender = Objects.requireNonNull(verifiedSender);
		this.invalidSyncedCommandsSender = Objects.requireNonNull(invalidSyncedCommandsSender);

		this.syncState = initialState;
	}

	public EventProcessor<SyncCheckTrigger> syncCheckTriggerEventProcessor() {
		return this::processSyncCheckTrigger;
	}

	private void processSyncCheckTrigger(SyncCheckTrigger syncCheckTrigger) {
		if (this.isIdleState()) {
			this.initSyncCheck();
		}
	}

	private void initSyncCheck() {
		final ImmutableSet<BFTNode> peersToAsk = this.choosePeersForSyncCheck();

		log.trace("LocalSync: Initializing sync check, about to ask {} peers for their status", peersToAsk.size());

		peersToAsk.forEach(peer -> statusRequestDispatcher.dispatch(peer, StatusRequest.create()));
		this.syncState = SyncState.SyncCheckState.init(this.syncState.getCurrentHeader(), peersToAsk);
		this.syncCheckReceiveStatusTimeoutDispatcher.dispatch(
			SyncCheckReceiveStatusTimeout.create(),
			this.syncConfig.syncCheckReceiveStatusTimeout()
		);
	}

	private ImmutableSet<BFTNode> choosePeersForSyncCheck() {
		final var allPeers = this.addressBook.peers().collect(Collectors.toList());
		Collections.shuffle(allPeers);
		return allPeers.stream()
			.limit(this.syncConfig.syncCheckMaxPeers())
			.map(peer -> BFTNode.create(peer.getSystem().getKey()))
			.collect(ImmutableSet.toImmutableSet());
	}

	public RemoteEventProcessor<StatusResponse> statusResponseEventProcessor() {
		return this::processStatusResponse;
	}

	private void processStatusResponse(BFTNode peer, StatusResponse statusResponse) {
		log.trace("LocalSync: Received status response {} from peer {}", statusResponse, peer);

		if (!isSyncCheckState()) {
			return;
		}
		final var syncCheckState = (SyncState.SyncCheckState) this.syncState;

		if (!syncCheckState.hasAskedPeer(peer)) {
			return; // we didn't ask this peer
		}

		if (syncCheckState.receivedResponseFrom(peer)) {
			return; // already got the response from this peer
		}

		final var newState = syncCheckState.withStatusResponse(peer, statusResponse);
		this.syncState = newState;

		if (newState.gotAllResponses()) {
			processPeerStatusResponsesAndStartSyncIfNeeded(newState); // we've got all the responses
		}
	}

	private void processPeerStatusResponsesAndStartSyncIfNeeded(SyncState.SyncCheckState syncCheckState) {
		// get the highest state that we received that is also higher than what we currently have
		final var maybeMaxPeerHeader = syncCheckState.responses().values()
			.stream()
			.map(StatusResponse::getHeader)
			.max(Comparator.comparing(VerifiedLedgerHeaderAndProof::getAccumulatorState, accComparator))
			.filter(h ->
				accComparator.compare(
					h.getAccumulatorState(),
					syncCheckState.getCurrentHeader().getAccumulatorState()
				) > 0
			);

		maybeMaxPeerHeader.ifPresentOrElse(
			maxPeerHeader -> {
				// start sync with all peers that are at the highest received state
				final var candidatePeers = syncCheckState.responses()
					.entrySet().stream()
					.filter(e ->
						accComparator.compare(
							e.getValue().getHeader().getAccumulatorState(),
							maxPeerHeader.getAccumulatorState()
						) == 0
					)
					.map(Map.Entry::getKey)
					.collect(ImmutableList.toImmutableList());

				this.startSync(candidatePeers, maxPeerHeader);
			},
			() -> {
				// there is no peer ahead of us, retry the sync check after some delay
				this.goToIdleAndScheduleSyncCheck();
			});
	}

	public EventProcessor<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutEventProcessor() {
		return this::processSyncCheckReceiveStatusTimeout;
	}

	private void processSyncCheckReceiveStatusTimeout(SyncCheckReceiveStatusTimeout syncCheckReceiveStatusTimeout) {
		if (!this.isSyncCheckState()) {
			return;
		}
		final var syncCheckState = (SyncState.SyncCheckState) this.syncState;

		if (!syncCheckState.responses().isEmpty()) {
			// we didn't get all the responses but we have some, try to sync with what we have
			this.processPeerStatusResponsesAndStartSyncIfNeeded(syncCheckState);
		} else {
			// we didn't get any response, retry the sync check after some delay
			this.goToIdleAndScheduleSyncCheck();
		}
	}

	private void goToIdleAndScheduleSyncCheck() {
		this.syncState = SyncState.IdleState.init(this.syncState.getCurrentHeader());
		this.syncCheckTriggerDispatcher.dispatch(SyncCheckTrigger.create(), syncConfig.syncCheckInterval());
	}

	private void startSync(ImmutableList<BFTNode> candidatePeers, VerifiedLedgerHeaderAndProof targetHeader) {
		log.trace("LocalSync: Syncing to target header {}, got {} candidate peers", targetHeader, candidatePeers.size());

		if (!this.isIdleState() && !isSyncCheckState()) {
			return;
		}

		this.syncState = SyncState.SyncingState.init(this.syncState.getCurrentHeader(), candidatePeers, targetHeader);
		this.processSync();
	}

	private void processSync() {
		if (!this.isSyncingState()) {
			return;
		}
		final var syncingState = (SyncState.SyncingState) this.syncState;

		this.updateSyncTargetDiffCounter(syncingState);

		if (isFullySynced(syncingState)) {
			log.trace("LocalSync: Fully synced to {}", syncingState.getTargetHeader());
			// we're fully synced, scheduling another sync check
			this.goToIdleAndScheduleSyncCheck();
			return;
		}

		if (syncingState.waitingForResponse()) {
			return; // we're already waiting for a response from peer
		}

		final Optional<BFTNode> peerToUse = syncingState.candidatePeers().stream()
			.filter(addressBook::hasBftNodePeer)
			.findFirst();

		peerToUse.ifPresentOrElse(
			peer -> this.sendSyncRequest(syncingState, peer),
			() -> {
				// there's no connected peer on our candidates list, starting a fresh sync check immediately
				this.syncState = SyncState.IdleState.init(syncingState.getCurrentHeader());
				this.initSyncCheck();
			}
		);
	}

	private void sendSyncRequest(SyncState.SyncingState syncingState, BFTNode peer) {
		log.trace("LocalSync: Sending sync request to {}", peer);

		final var newState = syncingState.withWaitingFor(peer);
		this.syncState = newState;

		this.syncRequestDispatcher.dispatch(peer, SyncRequest.create(newState.getCurrentHeader().toDto()));
		this.syncRequestTimeoutDispatcher.dispatch(
			SyncRequestTimeout.create(peer, newState.getCurrentHeader()),
			this.syncConfig.syncRequestTimeout()
		);
	}

	private boolean isFullySynced(SyncState.SyncingState syncingState) {
		return accComparator.compare(
			syncingState.getCurrentHeader().getAccumulatorState(),
			syncingState.getTargetHeader().getAccumulatorState()
		) >= 0;
	}

	public RemoteEventProcessor<SyncResponse> syncResponseEventProcessor() {
		return this::processSyncResponse;
	}

	private void processSyncResponse(BFTNode sender, SyncResponse syncResponse) {
		log.trace("LocalSync: Received sync response from {}", sender);

		if (!this.isSyncingState()) {
			return;
		}
		final var syncingState = (SyncState.SyncingState) this.syncState;

		if (!syncingState.waitingForResponseFrom(sender)) {
			log.trace("LocalSync: Received unexpected sync response from {}", sender);
			return;
		}

		// TODO: check validity of response
		if (syncResponse.getCommandsAndProof().getCommands().isEmpty()) {
			log.trace("LocalSync: Received empty sync response from {}", sender);
			// didn't receive any commands, remove from candidate peers and processSync
			this.syncState = syncingState
				.clearWaitingFor()
				.removeCandidate(sender);
			this.processSync();
		} else if (!this.verifyResponse(syncResponse)) {
			log.trace("LocalSync: Received invalid sync response from {}", sender);
			// validation failed, remove from candidate peers and processSync
			// TODO: also blacklist peer in PeerManager
			this.syncState = syncingState
				.clearWaitingFor()
				.removeCandidate(sender);
			invalidSyncedCommandsSender.sendInvalidSyncResponse(syncResponse);
			this.processSync();
		} else {
			this.syncState = syncingState.clearWaitingFor();
			// TODO: What if ledger update event never comes? Consider adding another timeout.
			this.verifiedSender.sendVerifiedSyncResponse(syncResponse);
		}
	}

	private boolean verifyResponse(SyncResponse syncResponse) {
		final DtoCommandsAndProof commandsAndProof = syncResponse.getCommandsAndProof();
		final AccumulatorState start = commandsAndProof.getHead().getLedgerHeader().getAccumulatorState();
		final AccumulatorState end = commandsAndProof.getTail().getLedgerHeader().getAccumulatorState();
		final ImmutableList<HashCode> hashes = commandsAndProof.getCommands().stream()
			.map(hasher::hash)
			.collect(ImmutableList.toImmutableList());

		return this.validatorSetVerifier.verifyValidatorSet(syncResponse)
			&& this.signaturesVerifier.verifyResponseSignatures(syncResponse)
			&& this.accumulatorVerifier.verify(start, hashes, end);
	}

	public EventProcessor<SyncRequestTimeout> syncRequestTimeoutEventProcessor() {
		return this::processSyncRequestTimeout;
	}

	private void processSyncRequestTimeout(SyncRequestTimeout syncRequestTimeout) {
		if (!this.isSyncingState()) {
			return;
		}
		final var syncingState = (SyncState.SyncingState) this.syncState;

		if (!syncingState.waitingForResponseFrom(syncRequestTimeout.getPeer())
			|| !syncingState.getCurrentHeader().equals(syncRequestTimeout.getCurrentHeader())) {
			return; // ignore, this timeout is no longer valid
		}

		log.trace("LocalSync: Sync request timeout from peer {}", syncRequestTimeout.getPeer());

		this.syncState = syncingState
			.clearWaitingFor()
			.removeCandidate(syncRequestTimeout.getPeer());
		this.processSync();
	}

	public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return this::processLedgerUpdate;
	}

	private void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		final var updatedHeader = ledgerUpdate.getTail();
		final var isNewerState = accComparator.compare(
			updatedHeader.getAccumulatorState(),
			this.syncState.getCurrentHeader().getAccumulatorState()
		) > 0;

		if (isNewerState) {
			this.syncState = this.syncState.withCurrentHeader(updatedHeader);
		}
		this.processSync();
	}

	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor() {
		return this::processLocalSyncRequest;
	}

	private void processLocalSyncRequest(LocalSyncRequest request) {
		final var requestedTarget = request.getTarget();

		if (this.isIdleState() || this.isSyncCheckState()) {
			this.startSync(request.getTargetNodes(), request.getTarget());
		} else if (this.isSyncingState()) {
			final var syncingState = (SyncState.SyncingState) this.syncState;

			// we're already syncing, update the target if needed and add candidate peers
			final var isNewerState =
				accComparator.compare(
					requestedTarget.getAccumulatorState(),
					syncingState.getTargetHeader().getAccumulatorState()
				) > 0;

			if (isNewerState) {
				this.syncState = syncingState
					.withTargetHeader(requestedTarget)
					.withCandidatePeers(request.getTargetNodes());
			} else {
				log.trace("LocalSync: skipping as already targeted {}", syncingState.getTargetHeader());
			}
		} else {
			throw new IllegalStateException("Unknown sync state");
		}
	}

	private void updateSyncTargetDiffCounter(SyncState.SyncingState syncingState) {
		final var stateVersionDiff =
			syncingState.getTargetHeader().getStateVersion() - syncingState.getCurrentHeader().getStateVersion();
		this.systemCounters.set(CounterType.SYNC_TARGET_CURRENT_DIFF, stateVersionDiff);
		this.systemCounters.set(
			CounterType.SYNC_TARGET_STATE_VERSION,
			syncingState.getTargetHeader().getAccumulatorState().getStateVersion()
		);
	}

	private boolean isIdleState() {
		return isInState(SyncState.IdleState.class);
	}

	private boolean isSyncCheckState() {
		return isInState(SyncState.SyncCheckState.class);
	}

	private boolean isSyncingState() {
		return isInState(SyncState.SyncingState.class);
	}

	private <T extends SyncState> boolean isInState(Class<T> cls) {
		return cls.isInstance(this.syncState);
	}

	public SyncState getSyncState() {
		return this.syncState;
	}
}
