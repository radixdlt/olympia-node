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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import com.radixdlt.sync.SyncState.IdleState;
import com.radixdlt.sync.SyncState.SyncCheckState;
import com.radixdlt.sync.SyncState.SyncingState;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.network.addressbook.AddressBook;

import java.util.Map;
import java.util.Comparator;
import java.util.Objects;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;

import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import com.radixdlt.sync.validation.RemoteSyncResponseSignaturesVerifier;
import com.radixdlt.sync.validation.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processes sync service messages and manages ledger sync state machine.
 * Thread-safety must be handled by caller.
 */
/* TODO: consider extracting some things away from this class (response validation, etc) as it's too monolithic. */
@NotThreadSafe
public final class LocalSyncService {

	public interface VerifiedSyncResponseSender {
		void sendVerifiedSyncResponse(SyncResponse remoteSyncResponse);
	}

	public interface InvalidSyncResponseSender {
		void sendInvalidSyncResponse(SyncResponse remoteSyncResponse);
	}

	private static final Logger log = LogManager.getLogger();

	private final RemoteEventDispatcher<StatusRequest> statusRequestDispatcher;
	private final ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutDispatcher;
	private final RemoteEventDispatcher<SyncRequest> syncRequestDispatcher;
	private final ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher;
	private final ScheduledEventDispatcher<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutDispatcher;
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

	private final ImmutableMap<Pair<? extends Class<?>, ? extends Class<?>>, Handler<?, ?>> handlers;

	private SyncState syncState;

	@Inject
	public LocalSyncService(
		RemoteEventDispatcher<StatusRequest> statusRequestDispatcher,
		ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutDispatcher,
		RemoteEventDispatcher<SyncRequest> syncRequestDispatcher,
		ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher,
		ScheduledEventDispatcher<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutDispatcher,
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
		this.statusRequestDispatcher = Objects.requireNonNull(statusRequestDispatcher);
		this.syncCheckReceiveStatusTimeoutDispatcher = Objects.requireNonNull(syncCheckReceiveStatusTimeoutDispatcher);
		this.syncRequestDispatcher = Objects.requireNonNull(syncRequestDispatcher);
		this.syncRequestTimeoutDispatcher = Objects.requireNonNull(syncRequestTimeoutDispatcher);
		this.syncLedgerUpdateTimeoutDispatcher = Objects.requireNonNull(syncLedgerUpdateTimeoutDispatcher);
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

		this.handlers = new ImmutableMap.Builder<Pair<? extends Class<?>, ? extends Class<?>>, Handler<?, ?>>()
			.put(handler(
				IdleState.class, SyncCheckTrigger.class,
				state -> unused -> this.initSyncCheck(state)
			))
			.put(remoteHandler(
				SyncCheckState.class, StatusResponse.class,
				state -> peer -> response -> this.processStatusResponse(state, peer, response)
			))
			.put(handler(
				SyncCheckState.class, SyncCheckReceiveStatusTimeout.class,
				state -> unused -> this.processSyncCheckReceiveStatusTimeout(state)
			))
			.put(remoteHandler(
				SyncingState.class, SyncResponse.class,
				state -> peer -> response -> this.processSyncResponse(state, peer, response)
			))
			.put(handler(
				SyncingState.class, SyncRequestTimeout.class,
				state -> timeout -> this.processSyncRequestTimeout(state, timeout)
			))
			.put(handler(
				IdleState.class, LedgerUpdate.class,
				state -> ledgerUpdate -> this.updateCurrentHeaderIfNeeded(state, ledgerUpdate)
			))
			.put(handler(
				SyncCheckState.class, LedgerUpdate.class,
				state -> ledgerUpdate -> this.updateCurrentHeaderIfNeeded(state, ledgerUpdate)
			))
			.put(handler(
				SyncingState.class, LedgerUpdate.class,
				state -> ledgerUpdate -> {
					final var newState = (SyncingState) this.updateCurrentHeaderIfNeeded(state, ledgerUpdate);
					return this.processSync(newState);
				}
			))
			.put(handler(
				IdleState.class, LocalSyncRequest.class,
				state -> request -> this.startSync(state, request.getTargetNodes(), request.getTarget())
			))
			.put(handler(
				SyncCheckState.class, LocalSyncRequest.class,
				state -> request -> this.startSync(state, request.getTargetNodes(), request.getTarget())
			))
			.put(handler(
				SyncingState.class, LocalSyncRequest.class,
				state -> request -> this.updateSyncingTarget(state, request)
			))
			.put(handler(
				SyncingState.class, SyncLedgerUpdateTimeout.class,
				state -> unused -> this.processSync(state)
			))
			.build();
	}

	private SyncState initSyncCheck(IdleState currentState) {
		final ImmutableSet<BFTNode> peersToAsk = this.choosePeersForSyncCheck();

		log.trace("LocalSync: Initializing sync check, about to ask {} peers for their status", peersToAsk.size());

		peersToAsk.forEach(peer -> statusRequestDispatcher.dispatch(peer, StatusRequest.create()));
		this.syncCheckReceiveStatusTimeoutDispatcher.dispatch(
			SyncCheckReceiveStatusTimeout.create(),
			this.syncConfig.syncCheckReceiveStatusTimeout()
		);

		return SyncCheckState.init(currentState.getCurrentHeader(), peersToAsk);
	}

	private ImmutableSet<BFTNode> choosePeersForSyncCheck() {
		final var allPeers = this.addressBook.peers().collect(Collectors.toList());
		Collections.shuffle(allPeers);
		return allPeers.stream()
			.limit(this.syncConfig.syncCheckMaxPeers())
			.map(peer -> BFTNode.create(peer.getSystem().getKey()))
			.collect(ImmutableSet.toImmutableSet());
	}

	private SyncState processStatusResponse(SyncCheckState currentState, BFTNode peer, StatusResponse statusResponse) {
		log.trace("LocalSync: Received status response {} from peer {}", statusResponse, peer);

		if (!currentState.hasAskedPeer(peer)) {
			return currentState; // we didn't ask this peer
		}

		if (currentState.receivedResponseFrom(peer)) {
			return currentState; // already got the response from this peer
		}

		final var newState = currentState.withStatusResponse(peer, statusResponse);

		if (newState.gotAllResponses()) {
			return processPeerStatusResponsesAndStartSyncIfNeeded(newState); // we've got all the responses
		} else {
			return newState;
		}
	}

	private SyncState processPeerStatusResponsesAndStartSyncIfNeeded(SyncCheckState currentState) {
		// get the highest state that we received that is also higher than what we currently have
		final var maybeMaxPeerHeader = currentState.responses().values()
			.stream()
			.map(StatusResponse::getHeader)
			.max(Comparator.comparing(VerifiedLedgerHeaderAndProof::getAccumulatorState, accComparator))
			.filter(h ->
				accComparator.compare(
					h.getAccumulatorState(),
					currentState.getCurrentHeader().getAccumulatorState()
				) > 0
			);

		return maybeMaxPeerHeader.map(maxPeerHeader -> {
			// start sync with all peers that are at the highest received state
			final var candidatePeers = currentState.responses()
				.entrySet().stream()
				.filter(e ->
					accComparator.compare(
						e.getValue().getHeader().getAccumulatorState(),
						maxPeerHeader.getAccumulatorState()
					) == 0
				)
				.map(Map.Entry::getKey)
				.collect(ImmutableList.toImmutableList());

			return this.startSync(currentState, candidatePeers, maxPeerHeader);
		})
		.orElseGet(() -> {
			// there is no peer ahead of us, go to idle and wait for another sync check
			return this.goToIdle(currentState);
		});
	}

	private SyncState processSyncCheckReceiveStatusTimeout(SyncCheckState currentState) {
		if (!currentState.responses().isEmpty()) {
			// we didn't get all the responses but we have some, try to sync with what we have
			return this.processPeerStatusResponsesAndStartSyncIfNeeded(currentState);
		} else {
			// we didn't get any response, go to idle and wait for another sync check
			return this.goToIdle(currentState);
		}
	}

	private SyncState goToIdle(SyncState currentState) {
		return IdleState.init(currentState.getCurrentHeader());
	}

	private SyncState startSync(
		SyncState currentState,
		ImmutableList<BFTNode> candidatePeers,
		VerifiedLedgerHeaderAndProof targetHeader
	) {
		log.trace("LocalSync: Syncing to target header {}, got {} candidate peers", targetHeader, candidatePeers.size());
		return this.processSync(SyncingState.init(currentState.getCurrentHeader(), candidatePeers, targetHeader));
	}

	private SyncState processSync(SyncingState currentState) {
		this.updateSyncTargetDiffCounter(currentState);

		if (isFullySynced(currentState)) {
			log.trace("LocalSync: Fully synced to {}", currentState.getTargetHeader());
			// we're fully synced, go to idle and wait for another sync check
			return this.goToIdle(currentState);
		}

		if (currentState.waitingForResponse()) {
			return currentState; // we're already waiting for a response from peer
		}

		final Optional<BFTNode> peerToUse = currentState.candidatePeers().stream()
			.filter(addressBook::hasBftNodePeer)
			.findFirst();

		return peerToUse
			.map(peer -> this.sendSyncRequest(currentState, peer))
			.orElseGet(() -> {
				// there's no connected peer on our candidates list, starting a fresh sync check immediately
				return this.initSyncCheck(IdleState.init(currentState.getCurrentHeader()));
			});
	}

	private SyncState sendSyncRequest(SyncingState currentState, BFTNode peer) {
		log.trace("LocalSync: Sending sync request to {}", peer);

		final var currentHeader = currentState.getCurrentHeader();

		this.syncRequestDispatcher.dispatch(peer, SyncRequest.create(currentHeader.toDto()));
		this.syncRequestTimeoutDispatcher.dispatch(
			SyncRequestTimeout.create(peer, currentHeader),
			this.syncConfig.syncRequestTimeout()
		);

		return currentState.withWaitingFor(peer);
	}

	private boolean isFullySynced(SyncState.SyncingState syncingState) {
		return accComparator.compare(
			syncingState.getCurrentHeader().getAccumulatorState(),
			syncingState.getTargetHeader().getAccumulatorState()
		) >= 0;
	}

	private SyncState processSyncResponse(SyncingState currentState, BFTNode sender, SyncResponse syncResponse) {
		log.trace("LocalSync: Received sync response from {}", sender);

		if (!currentState.waitingForResponseFrom(sender)) {
			log.trace("LocalSync: Received unexpected sync response from {}", sender);
			return currentState;
		}

		// TODO: check validity of response
		if (syncResponse.getCommandsAndProof().getCommands().isEmpty()) {
			log.trace("LocalSync: Received empty sync response from {}", sender);
			// didn't receive any commands, remove from candidate peers and processSync
			return this.processSync(
				currentState
					.clearWaitingFor()
					.removeCandidate(sender)
			);
		} else if (!this.verifyResponse(syncResponse)) {
			log.trace("LocalSync: Received invalid sync response from {}", sender);
			// validation failed, remove from candidate peers and processSync
			// TODO: also blacklist peer in PeerManager
			invalidSyncedCommandsSender.sendInvalidSyncResponse(syncResponse);
			return this.processSync(
				currentState
					.clearWaitingFor()
					.removeCandidate(sender)
			);
		} else {
			this.syncLedgerUpdateTimeoutDispatcher.dispatch(
				SyncLedgerUpdateTimeout.create(),
				500L
			);
			this.verifiedSender.sendVerifiedSyncResponse(syncResponse);
			return currentState.clearWaitingFor();
		}
	}

	private boolean verifyResponse(SyncResponse syncResponse) {
		final var commandsAndProof = syncResponse.getCommandsAndProof();
		final var start = commandsAndProof.getHead().getLedgerHeader().getAccumulatorState();
		final var end = commandsAndProof.getTail().getLedgerHeader().getAccumulatorState();
		final var hashes = commandsAndProof.getCommands().stream()
			.map(hasher::hash)
			.collect(ImmutableList.toImmutableList());

		return this.validatorSetVerifier.verifyValidatorSet(syncResponse)
			&& this.signaturesVerifier.verifyResponseSignatures(syncResponse)
			&& this.accumulatorVerifier.verify(start, hashes, end);
	}

	private SyncState processSyncRequestTimeout(SyncingState currentState, SyncRequestTimeout syncRequestTimeout) {
		if (!currentState.waitingForResponseFrom(syncRequestTimeout.getPeer())
			|| !currentState.getCurrentHeader().equals(syncRequestTimeout.getCurrentHeader())) {
			return currentState; // ignore, this timeout is no longer valid
		}

		log.trace("LocalSync: Sync request timeout from peer {}", syncRequestTimeout.getPeer());

		return this.processSync(
			currentState
				.clearWaitingFor()
				.removeCandidate(syncRequestTimeout.getPeer())
		);
	}

	private SyncState updateCurrentHeaderIfNeeded(SyncState currentState, LedgerUpdate ledgerUpdate) {
		final var updatedHeader = ledgerUpdate.getTail();
		final var isNewerState = accComparator.compare(
				updatedHeader.getAccumulatorState(),
				currentState.getCurrentHeader().getAccumulatorState()
		) > 0;

		if (isNewerState) {
			return currentState.withCurrentHeader(updatedHeader);
		} else {
			return currentState;
		}
	}

	private SyncingState updateSyncingTarget(SyncingState currentState, LocalSyncRequest request) {
		// we're already syncing, update the target if needed and add candidate peers
		final var isNewerState =
			accComparator.compare(
					request.getTarget().getAccumulatorState(),
					currentState.getTargetHeader().getAccumulatorState()
			) > 0;

		if (isNewerState) {
			return currentState
				.withTargetHeader(request.getTarget())
				.withCandidatePeers(request.getTargetNodes());
		} else {
			log.trace("LocalSync: skipping as already targeted {}", currentState.getTargetHeader());
			return currentState;
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

	public SyncState getSyncState() {
		return this.syncState;
	}

	public EventProcessor<SyncCheckTrigger> syncCheckTriggerEventProcessor() {
		return (event) -> this.processEvent(SyncCheckTrigger.class, event);
	}

	public RemoteEventProcessor<StatusResponse> statusResponseEventProcessor() {
		return (peer, event) -> this.processRemoteEvent(StatusResponse.class, peer, event);
	}

	public EventProcessor<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutEventProcessor() {
		return (event) -> this.processEvent(SyncCheckReceiveStatusTimeout.class, event);
	}

	public RemoteEventProcessor<SyncResponse> syncResponseEventProcessor() {
		return (peer, event) -> this.processRemoteEvent(SyncResponse.class, peer, event);
	}

	public EventProcessor<SyncRequestTimeout> syncRequestTimeoutEventProcessor() {
		return (event) -> this.processEvent(SyncRequestTimeout.class, event);
	}

	public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return (event) -> this.processEvent(LedgerUpdate.class, event);
	}

	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor() {
		return (event) -> this.processEvent(LocalSyncRequest.class, event);
	}

	public EventProcessor<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutProcessor() {
		return (event) -> this.processEvent(SyncLedgerUpdateTimeout.class, event);
	}

	private <T> void processEvent(Class<T> eventClass, T event) {
		@SuppressWarnings("unchecked")
		final var maybeHandler =
			(Handler<Object, Object>) this.handlers.get(Pair.of(this.syncState.getClass(), eventClass));
		if (maybeHandler != null) {
			this.syncState = maybeHandler.handle(this.syncState, event);
		}
	}

	private <T> void processRemoteEvent(Class<T> eventClass, BFTNode peer, T event) {
		@SuppressWarnings("unchecked")
		final var maybeHandler =
			(Handler<Object, Object>) this.handlers.get(Pair.of(this.syncState.getClass(), eventClass));
		if (maybeHandler != null) {
			this.syncState = maybeHandler.handle(this.syncState, peer, event);
		}
	}

	private <S, T> Map.Entry<Pair<Class<S>, Class<T>>, Handler<S, T>> handler(
		Class<S> stateClass,
		Class<T> eventClass,
		Function<S, Function<T, SyncState>> fn
	) {
		return Map.entry(Pair.of(stateClass, eventClass), new Handler<>(fn));
	}

	private <S, T> Map.Entry<Pair<Class<S>, Class<T>>, Handler<S, T>> remoteHandler(
		Class<S> stateClass,
		Class<T> eventClass,
		Function<S, Function<BFTNode, Function<T, SyncState>>> fn
	) {
		return Map.entry(Pair.of(stateClass, eventClass), new Handler<>(new Object(), fn));
	}

	private static final class Handler<S, T> {
		private Function<S, Function<T, SyncState>> handleEvent;
		private Function<S, Function<BFTNode, Function<T, SyncState>>> handleRemoteEvent;

		Handler(Function<S, Function<T, SyncState>> fn) {
			this.handleEvent = fn;
		}

		/* need another param to be able to distinguish the methods after type erasure */
		Handler(Object erasureFix, Function<S, Function<BFTNode, Function<T, SyncState>>> fn) {
			this.handleRemoteEvent = fn;
		}

		SyncState handle(S currentState, T event) {
			return this.handleEvent.apply(currentState).apply(event);
		}

		SyncState handle(S currentState, BFTNode peer, T event) {
			return this.handleRemoteEvent.apply(currentState).apply(peer).apply(event);
		}
	}
}
