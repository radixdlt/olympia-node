/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.sync.SyncState.IdleState;
import com.radixdlt.sync.SyncState.SyncCheckState;
import com.radixdlt.sync.SyncState.SyncingState;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import com.radixdlt.sync.validation.RemoteSyncResponseSignaturesVerifier;
import com.radixdlt.sync.validation.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.utils.Pair;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processes sync service messages and manages ledger sync state machine. Thread-safety must be
 * handled by caller.
 */
/* TODO: consider extracting some things away from this class (response validation, etc) as it's too monolithic. */
@NotThreadSafe
public final class LocalSyncService {

  public interface VerifiedSyncResponseHandler {
    void handleVerifiedSyncResponse(SyncResponse syncResponse);
  }

  public interface InvalidSyncResponseHandler {
    void handleInvalidSyncResponse(BFTNode sender, SyncResponse syncResponse);
  }

  private static final Logger log = LogManager.getLogger();

  private final AtomicLong requestIdCounter = new AtomicLong();
  private final RemoteEventDispatcher<StatusRequest> statusRequestDispatcher;
  private final ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout>
      syncCheckReceiveStatusTimeoutDispatcher;
  private final RemoteEventDispatcher<SyncRequest> syncRequestDispatcher;
  private final ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher;
  private final ScheduledEventDispatcher<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutDispatcher;
  private final SyncConfig syncConfig;
  private final SystemCounters systemCounters;
  private final PeersView peersView;
  private final Comparator<AccumulatorState> accComparator;
  private final RemoteSyncResponseValidatorSetVerifier validatorSetVerifier;
  private final RemoteSyncResponseSignaturesVerifier signaturesVerifier;
  private final LedgerAccumulatorVerifier accumulatorVerifier;
  private final VerifiedSyncResponseHandler verifiedSyncResponseHandler;
  private final InvalidSyncResponseHandler invalidSyncResponseHandler;

  private final ImmutableMap<Pair<? extends Class<?>, ? extends Class<?>>, Handler<?, ?>> handlers;

  private SyncState syncState;

  @Inject
  public LocalSyncService(
      RemoteEventDispatcher<StatusRequest> statusRequestDispatcher,
      ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout>
          syncCheckReceiveStatusTimeoutDispatcher,
      RemoteEventDispatcher<SyncRequest> syncRequestDispatcher,
      ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher,
      ScheduledEventDispatcher<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutDispatcher,
      SyncConfig syncConfig,
      SystemCounters systemCounters,
      PeersView peersView,
      Comparator<AccumulatorState> accComparator,
      RemoteSyncResponseValidatorSetVerifier validatorSetVerifier,
      RemoteSyncResponseSignaturesVerifier signaturesVerifier,
      LedgerAccumulatorVerifier accumulatorVerifier,
      VerifiedSyncResponseHandler verifiedSyncResponseHandler,
      InvalidSyncResponseHandler invalidSyncResponseHandler,
      SyncState initialState) {
    this.statusRequestDispatcher = Objects.requireNonNull(statusRequestDispatcher);
    this.syncCheckReceiveStatusTimeoutDispatcher =
        Objects.requireNonNull(syncCheckReceiveStatusTimeoutDispatcher);
    this.syncRequestDispatcher = Objects.requireNonNull(syncRequestDispatcher);
    this.syncRequestTimeoutDispatcher = Objects.requireNonNull(syncRequestTimeoutDispatcher);
    this.syncLedgerUpdateTimeoutDispatcher =
        Objects.requireNonNull(syncLedgerUpdateTimeoutDispatcher);
    this.syncConfig = Objects.requireNonNull(syncConfig);
    this.systemCounters = Objects.requireNonNull(systemCounters);
    this.peersView = Objects.requireNonNull(peersView);
    this.accComparator = Objects.requireNonNull(accComparator);
    this.validatorSetVerifier = Objects.requireNonNull(validatorSetVerifier);
    this.signaturesVerifier = Objects.requireNonNull(signaturesVerifier);
    this.accumulatorVerifier = Objects.requireNonNull(accumulatorVerifier);
    this.verifiedSyncResponseHandler = Objects.requireNonNull(verifiedSyncResponseHandler);
    this.invalidSyncResponseHandler = Objects.requireNonNull(invalidSyncResponseHandler);

    this.syncState = initialState;

    this.handlers =
        new ImmutableMap.Builder<Pair<? extends Class<?>, ? extends Class<?>>, Handler<?, ?>>()
            .put(
                handler(
                    IdleState.class,
                    SyncCheckTrigger.class,
                    state -> unused -> this.initSyncCheck(state)))
            .put(
                remoteHandler(
                    SyncCheckState.class,
                    StatusResponse.class,
                    state -> peer -> response -> this.processStatusResponse(state, peer, response)))
            .put(
                handler(
                    SyncCheckState.class,
                    SyncCheckReceiveStatusTimeout.class,
                    state -> unused -> this.processSyncCheckReceiveStatusTimeout(state)))
            .put(
                remoteHandler(
                    SyncingState.class,
                    SyncResponse.class,
                    state -> peer -> response -> this.processSyncResponse(state, peer, response)))
            .put(
                handler(
                    SyncingState.class,
                    SyncRequestTimeout.class,
                    state -> timeout -> this.processSyncRequestTimeout(state, timeout)))
            .put(
                handler(
                    IdleState.class,
                    LedgerUpdate.class,
                    state -> ledgerUpdate -> this.updateCurrentHeaderIfNeeded(state, ledgerUpdate)))
            .put(
                handler(
                    SyncCheckState.class,
                    LedgerUpdate.class,
                    state -> ledgerUpdate -> this.updateCurrentHeaderIfNeeded(state, ledgerUpdate)))
            .put(
                handler(
                    SyncingState.class,
                    LedgerUpdate.class,
                    state ->
                        ledgerUpdate -> {
                          final var newState =
                              (SyncingState) this.updateCurrentHeaderIfNeeded(state, ledgerUpdate);
                          return this.processSync(newState);
                        }))
            .put(
                handler(
                    IdleState.class,
                    LocalSyncRequest.class,
                    state ->
                        request ->
                            this.startSync(state, request.getTargetNodes(), request.getTarget())))
            .put(
                handler(
                    SyncCheckState.class,
                    LocalSyncRequest.class,
                    state ->
                        request ->
                            this.startSync(state, request.getTargetNodes(), request.getTarget())))
            .put(
                handler(
                    SyncingState.class,
                    LocalSyncRequest.class,
                    state ->
                        request ->
                            this.updateTargetIfNeeded(
                                state, request.getTargetNodes(), request.getTarget())))
            .put(
                remoteHandler(
                    IdleState.class,
                    LedgerStatusUpdate.class,
                    state ->
                        peer ->
                            request ->
                                this.startSync(state, ImmutableList.of(peer), request.getHeader())))
            .put(
                remoteHandler(
                    SyncingState.class,
                    LedgerStatusUpdate.class,
                    state ->
                        peer ->
                            ledgerStatusUpdate ->
                                this.updateTargetIfNeeded(
                                    state, ImmutableList.of(peer), ledgerStatusUpdate.getHeader())))
            .put(
                handler(
                    SyncingState.class,
                    SyncLedgerUpdateTimeout.class,
                    state -> event -> this.processSyncLedgerUpdateTimeout(state, event)))
            .build();
  }

  private SyncState initSyncCheck(IdleState currentState) {
    final ImmutableSet<BFTNode> peersToAsk = this.choosePeersForSyncCheck();

    log.trace(
        "LocalSync: Initializing sync check, about to ask {} peers for their status",
        peersToAsk.size());

    peersToAsk.forEach(peer -> statusRequestDispatcher.dispatch(peer, StatusRequest.create()));
    this.syncCheckReceiveStatusTimeoutDispatcher.dispatch(
        SyncCheckReceiveStatusTimeout.create(), this.syncConfig.syncCheckReceiveStatusTimeout());

    return SyncCheckState.init(currentState.getCurrentHeader(), peersToAsk);
  }

  private ImmutableSet<BFTNode> choosePeersForSyncCheck() {
    final var allPeers = this.peersView.peers().collect(Collectors.toList());
    Collections.shuffle(allPeers);
    return allPeers.stream()
        .limit(this.syncConfig.syncCheckMaxPeers())
        .map(PeersView.PeerInfo::bftNode)
        .collect(ImmutableSet.toImmutableSet());
  }

  private SyncState processStatusResponse(
      SyncCheckState currentState, BFTNode peer, StatusResponse statusResponse) {
    log.trace("LocalSync: Received status response {} from peer {}", statusResponse, peer);

    if (!currentState.hasAskedPeer(peer)) {
      return currentState; // we didn't ask this peer
    }

    if (currentState.receivedResponseFrom(peer)) {
      return currentState; // already got the response from this peer
    }

    final var newState = currentState.withStatusResponse(peer, statusResponse);

    if (newState.gotAllResponses()) {
      return processPeerStatusResponsesAndStartSyncIfNeeded(
          newState); // we've got all the responses
    } else {
      return newState;
    }
  }

  private SyncState processPeerStatusResponsesAndStartSyncIfNeeded(SyncCheckState currentState) {
    // get the highest state that we received that is also higher than what we currently have
    final var maybeMaxPeerHeader =
        currentState.responses().values().stream()
            .map(StatusResponse::getHeader)
            .max(Comparator.comparing(LedgerProof::getAccumulatorState, accComparator))
            .filter(
                h ->
                    accComparator.compare(
                            h.getAccumulatorState(),
                            currentState.getCurrentHeader().getAccumulatorState())
                        > 0);

    return maybeMaxPeerHeader
        .map(
            maxPeerHeader -> {
              // start sync with all peers that are at the highest received state
              final var candidatePeers =
                  currentState.responses().entrySet().stream()
                      .filter(
                          e ->
                              accComparator.compare(
                                      e.getValue().getHeader().getAccumulatorState(),
                                      maxPeerHeader.getAccumulatorState())
                                  == 0)
                      .map(Map.Entry::getKey)
                      .collect(ImmutableList.toImmutableList());

              return this.startSync(currentState, candidatePeers, maxPeerHeader);
            })
        .orElseGet(
            () -> {
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
      SyncState currentState, ImmutableList<BFTNode> candidatePeers, LedgerProof targetHeader) {
    log.trace(
        "LocalSync: Syncing to target header {}, got {} candidate peers",
        targetHeader,
        candidatePeers.size());
    return this.processSync(
        SyncingState.init(currentState.getCurrentHeader(), candidatePeers, targetHeader));
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

    final var candidatePeerResult = currentState.fetchNextCandidatePeer();
    final var stateWithUpdatedQueue = candidatePeerResult.getFirst();
    final var maybePeerToUse = candidatePeerResult.getSecond();

    return maybePeerToUse
        .map(peerToUse -> this.sendSyncRequest(stateWithUpdatedQueue, peerToUse))
        .orElseGet(
            () -> {
              // there's no connected peer on our candidates list, starting a fresh sync check
              // immediately
              return this.initSyncCheck(IdleState.init(stateWithUpdatedQueue.getCurrentHeader()));
            });
  }

  private SyncState sendSyncRequest(SyncingState currentState, BFTNode peer) {
    log.trace("LocalSync: Sending sync request to {}", peer);

    final var currentHeader = currentState.getCurrentHeader();

    final var requestId = requestIdCounter.incrementAndGet();
    this.syncRequestDispatcher.dispatch(peer, SyncRequest.create(currentHeader.toDto()));
    this.syncRequestTimeoutDispatcher.dispatch(
        SyncRequestTimeout.create(peer, requestId), this.syncConfig.syncRequestTimeout());

    return currentState.withPendingRequest(peer, requestId);
  }

  private boolean isFullySynced(SyncState.SyncingState syncingState) {
    return accComparator.compare(
            syncingState.getCurrentHeader().getAccumulatorState(),
            syncingState.getTargetHeader().getAccumulatorState())
        >= 0;
  }

  private SyncState processSyncResponse(
      SyncingState currentState, BFTNode sender, SyncResponse syncResponse) {
    log.trace("LocalSync: Received sync response from {}", sender);

    if (!currentState.waitingForResponseFrom(sender)) {
      log.warn("LocalSync: Received unexpected sync response from {}", sender);
      return currentState;
    }

    // TODO: check validity of response
    if (syncResponse.getTxnsAndProof().getTxns().isEmpty()) {
      log.warn("LocalSync: Received empty sync response from {}", sender);
      // didn't receive any commands, remove from candidate peers and processSync
      return this.processSync(currentState.clearPendingRequest().removeCandidate(sender));
    } else if (!this.verifyResponse(syncResponse)) {
      log.warn("LocalSync: Received invalid sync response {} from {}", syncResponse, sender);
      // validation failed, remove from candidate peers and processSync
      invalidSyncResponseHandler.handleInvalidSyncResponse(sender, syncResponse);
      return this.processSync(currentState.clearPendingRequest().removeCandidate(sender));
    } else {
      this.syncLedgerUpdateTimeoutDispatcher.dispatch(
          SyncLedgerUpdateTimeout.create(currentState.getCurrentHeader().getStateVersion()), 1000L);
      this.verifiedSyncResponseHandler.handleVerifiedSyncResponse(syncResponse);
      return currentState.clearPendingRequest();
    }
  }

  private boolean verifyResponse(SyncResponse syncResponse) {
    final var commandsAndProof = syncResponse.getTxnsAndProof();
    final var start = commandsAndProof.getHead().getLedgerHeader().getAccumulatorState();
    final var end = commandsAndProof.getTail().getLedgerHeader().getAccumulatorState();
    final var hashes =
        commandsAndProof.getTxns().stream()
            .map(txn -> txn.getId().asHashCode())
            .collect(ImmutableList.toImmutableList());

    if (!this.validatorSetVerifier.verifyValidatorSet(syncResponse)) {
      log.warn("Invalid validator set");
      return false;
    }

    if (!this.signaturesVerifier.verifyResponseSignatures(syncResponse)) {
      log.warn("Invalid signatures");
      return false;
    }

    if (!this.accumulatorVerifier.verify(start, hashes, end)) {
      log.warn("Invalid accumulator");
      return false;
    }

    return true;
  }

  private SyncState processSyncRequestTimeout(
      SyncingState currentState, SyncRequestTimeout syncRequestTimeout) {
    final var timeoutMatchesRequest =
        currentState.getPendingRequest().stream()
            .anyMatch(
                pr ->
                    pr.getRequestId() == syncRequestTimeout.getRequestId()
                        && pr.getPeer().equals(syncRequestTimeout.getPeer()));

    if (!timeoutMatchesRequest) {
      return currentState; // ignore, this timeout is no longer valid
    }

    log.trace("LocalSync: Sync request timeout from peer {}", syncRequestTimeout.getPeer());

    return this.processSync(
        currentState.clearPendingRequest().removeCandidate(syncRequestTimeout.getPeer()));
  }

  private SyncState processSyncLedgerUpdateTimeout(
      SyncingState currentState, SyncLedgerUpdateTimeout event) {
    if (event.stateVersion() != currentState.getCurrentHeader().getStateVersion()) {
      return currentState; // obsolete timeout event; ignore
    } else {
      return this.processSync(currentState);
    }
  }

  private SyncState updateCurrentHeaderIfNeeded(SyncState currentState, LedgerUpdate ledgerUpdate) {
    final var updatedHeader = ledgerUpdate.getTail();
    final var isNewerState =
        accComparator.compare(
                updatedHeader.getAccumulatorState(),
                currentState.getCurrentHeader().getAccumulatorState())
            > 0;

    if (isNewerState) {
      final var newState = currentState.withCurrentHeader(updatedHeader);
      return this.updateSyncTargetDiffCounter(newState);
    } else {
      return currentState;
    }
  }

  private SyncingState updateTargetIfNeeded(
      SyncingState currentState, ImmutableList<BFTNode> peers, LedgerProof header) {
    final var isNewerState =
        accComparator.compare(
                header.getAccumulatorState(), currentState.getTargetHeader().getAccumulatorState())
            > 0;

    if (isNewerState) {
      final var newState = currentState.withTargetHeader(header).addCandidatePeers(peers);
      return this.updateSyncTargetDiffCounter(newState);
    } else {
      log.trace("LocalSync: skipping as already targeted {}", currentState.getTargetHeader());
      return currentState;
    }
  }

  private <T extends SyncState> T updateSyncTargetDiffCounter(T syncState) {
    if (syncState instanceof final SyncingState syncingState) {
      this.systemCounters.set(
          CounterType.SYNC_CURRENT_STATE_VERSION,
          syncingState.getCurrentHeader().getStateVersion());
      this.systemCounters.set(
          CounterType.SYNC_TARGET_STATE_VERSION,
          Math.max(
              syncingState.getTargetHeader().getStateVersion(),
              syncingState.getTargetHeader().getAccumulatorState().getStateVersion()));
    } else {
      this.systemCounters.set(
          CounterType.SYNC_CURRENT_STATE_VERSION, syncState.getCurrentHeader().getStateVersion());
      this.systemCounters.set(
          CounterType.SYNC_TARGET_STATE_VERSION, syncState.getCurrentHeader().getStateVersion());
    }

    return syncState;
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

  public EventProcessor<SyncCheckReceiveStatusTimeout>
      syncCheckReceiveStatusTimeoutEventProcessor() {
    return (event) -> this.processEvent(SyncCheckReceiveStatusTimeout.class, event);
  }

  public RemoteEventProcessor<SyncResponse> syncResponseEventProcessor() {
    return (peer, event) -> this.processRemoteEvent(SyncResponse.class, peer, event);
  }

  public RemoteEventProcessor<LedgerStatusUpdate> ledgerStatusUpdateEventProcessor() {
    return (peer, event) -> this.processRemoteEvent(LedgerStatusUpdate.class, peer, event);
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
      Class<S> stateClass, Class<T> eventClass, Function<S, Function<T, SyncState>> fn) {
    return Map.entry(Pair.of(stateClass, eventClass), new Handler<>(fn));
  }

  private <S, T> Map.Entry<Pair<Class<S>, Class<T>>, Handler<S, T>> remoteHandler(
      Class<S> stateClass,
      Class<T> eventClass,
      Function<S, Function<BFTNode, Function<T, SyncState>>> fn) {
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
