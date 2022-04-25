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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.DtoTxnsAndProof;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.network.p2p.PeersView.PeerInfo;
import com.radixdlt.sync.LocalSyncService.InvalidSyncResponseHandler;
import com.radixdlt.sync.LocalSyncService.VerifiedSyncResponseHandler;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class LocalSyncServiceTest {
  private LocalSyncService localSyncService;
  private RemoteEventDispatcher<StatusRequest> statusRequestDispatcher;
  private ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout>
      syncCheckReceiveStatusTimeoutDispatcher;
  private RemoteEventDispatcher<SyncRequest> syncRequestDispatcher;
  private ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher;
  private ScheduledEventDispatcher<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutDispatcher;
  private SyncConfig syncConfig;
  private SystemCounters systemCounters;
  private PeersView peersView;
  private Comparator<AccumulatorState> accComparator;
  private RemoteSyncResponseValidatorSetVerifier validatorSetVerifier;
  private RemoteSyncResponseSignaturesVerifier signaturesVerifier;
  private LedgerAccumulatorVerifier accumulatorVerifier;
  private VerifiedSyncResponseHandler verifiedSyncResponseHandler;
  private InvalidSyncResponseHandler invalidSyncResponseHandler;

  @Before
  public void setUp() {
    this.statusRequestDispatcher = rmock(RemoteEventDispatcher.class);
    this.syncCheckReceiveStatusTimeoutDispatcher = rmock(ScheduledEventDispatcher.class);
    this.syncRequestDispatcher = rmock(RemoteEventDispatcher.class);
    this.syncRequestTimeoutDispatcher = rmock(ScheduledEventDispatcher.class);
    this.syncLedgerUpdateTimeoutDispatcher = rmock(ScheduledEventDispatcher.class);
    this.syncConfig = SyncConfig.of(1000L, 10, 10000L);
    this.systemCounters = mock(SystemCounters.class);
    this.peersView = mock(PeersView.class);
    this.accComparator = Comparator.comparingLong(AccumulatorState::getStateVersion);
    this.validatorSetVerifier = mock(RemoteSyncResponseValidatorSetVerifier.class);
    this.signaturesVerifier = mock(RemoteSyncResponseSignaturesVerifier.class);
    this.accumulatorVerifier = mock(LedgerAccumulatorVerifier.class);
    this.verifiedSyncResponseHandler = mock(VerifiedSyncResponseHandler.class);
    this.invalidSyncResponseHandler = mock(InvalidSyncResponseHandler.class);
  }

  private void setupSyncServiceWithState(SyncState syncState) {
    this.localSyncService =
        new LocalSyncService(
            statusRequestDispatcher,
            syncCheckReceiveStatusTimeoutDispatcher,
            syncRequestDispatcher,
            syncRequestTimeoutDispatcher,
            syncLedgerUpdateTimeoutDispatcher,
            syncConfig,
            systemCounters,
            peersView,
            accComparator,
            validatorSetVerifier,
            signaturesVerifier,
            accumulatorVerifier,
            verifiedSyncResponseHandler,
            invalidSyncResponseHandler,
            syncState);
  }

  @Test
  public void when_sync_check_is_triggered_at_idle__then_should_ask_peers_for_their_statuses() {
    final var peer1 = createPeer();
    final var peer2 = createPeer();
    final var peer3 = createPeer();

    setupPeersView(peer1, peer2, peer3);

    final LedgerProof currentHeader = mock(LedgerProof.class);
    this.setupSyncServiceWithState(SyncState.IdleState.init(currentHeader));

    this.localSyncService.syncCheckTriggerEventProcessor().process(SyncCheckTrigger.create());

    verify(statusRequestDispatcher, times(1)).dispatch(eq(peer1), any());
    verify(statusRequestDispatcher, times(1)).dispatch(eq(peer2), any());
    verify(statusRequestDispatcher, times(1)).dispatch(eq(peer2), any());
  }

  @Test
  public void when_sync_check_is_triggered_at_non_idle__then_should_be_ignored() {
    final LedgerProof currentHeader = mock(LedgerProof.class);

    this.setupSyncServiceWithState(SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of()));
    this.localSyncService.syncCheckTriggerEventProcessor().process(SyncCheckTrigger.create());

    this.setupSyncServiceWithState(
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(), currentHeader));
    this.localSyncService.syncCheckTriggerEventProcessor().process(SyncCheckTrigger.create());

    verifyNoMoreInteractions(peersView);
    verifyNoMoreInteractions(statusRequestDispatcher);
  }

  @Test
  public void when_status_response_received_at_non_sync_check__then_should_be_ignored() {
    final LedgerProof currentHeader = mock(LedgerProof.class);
    final LedgerProof statusHeader = mock(LedgerProof.class);
    final BFTNode sender = createPeer();

    this.setupSyncServiceWithState(SyncState.IdleState.init(currentHeader));
    this.localSyncService
        .statusResponseEventProcessor()
        .process(sender, StatusResponse.create(statusHeader));

    this.setupSyncServiceWithState(
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(), currentHeader));
    this.localSyncService
        .statusResponseEventProcessor()
        .process(sender, StatusResponse.create(statusHeader));

    verifyNoMoreInteractions(peersView);
    verifyNoMoreInteractions(statusRequestDispatcher);
    verifyNoMoreInteractions(syncRequestDispatcher);
    verifyNoMoreInteractions(syncRequestTimeoutDispatcher);
  }

  @Test
  public void when_unexpected_status_response_received__then_should_be_ignored() {
    final LedgerProof currentHeader = mock(LedgerProof.class);
    final LedgerProof statusHeader = mock(LedgerProof.class);
    final BFTNode expectedPeer = createPeer();
    final BFTNode unexpectedPeer = createPeer();

    this.setupSyncServiceWithState(
        SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(expectedPeer)));
    this.localSyncService
        .statusResponseEventProcessor()
        .process(unexpectedPeer, StatusResponse.create(statusHeader));

    verifyNoMoreInteractions(peersView);
    verifyNoMoreInteractions(statusRequestDispatcher);
    verifyNoMoreInteractions(syncRequestDispatcher);
    verifyNoMoreInteractions(syncRequestTimeoutDispatcher);
  }

  @Test
  public void when_duplicate_status_response_received__then_should_be_ignored() {
    final LedgerProof currentHeader = mock(LedgerProof.class);
    final LedgerProof statusHeader = mock(LedgerProof.class);
    final BFTNode expectedPeer = createPeer();
    final BFTNode alreadyReceivedPeer = createPeer();

    final var syncState =
        SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(expectedPeer))
            .withStatusResponse(alreadyReceivedPeer, StatusResponse.create(statusHeader));

    this.setupSyncServiceWithState(syncState);
    this.localSyncService
        .statusResponseEventProcessor()
        .process(alreadyReceivedPeer, StatusResponse.create(statusHeader));

    verifyNoMoreInteractions(peersView);
    verifyNoMoreInteractions(statusRequestDispatcher);
    verifyNoMoreInteractions(syncRequestDispatcher);
    verifyNoMoreInteractions(syncRequestTimeoutDispatcher);
  }

  @Test
  public void when_all_status_responses_received__then_should_start_sync() {
    final LedgerProof currentHeader = createHeaderAtStateVersion(10L);
    final LedgerProof statusHeader1 = createHeaderAtStateVersion(2L);
    final LedgerProof statusHeader2 = createHeaderAtStateVersion(20L);
    final LedgerProof statusHeader3 = createHeaderAtStateVersion(15L);
    final BFTNode waiting1 = createPeer();
    final BFTNode waiting2 = createPeer();
    final BFTNode waiting3 = createPeer();

    final var syncState =
        SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(waiting1, waiting2, waiting3));
    this.setupSyncServiceWithState(syncState);

    setupPeersView(waiting2);

    this.localSyncService
        .statusResponseEventProcessor()
        .process(waiting1, StatusResponse.create(statusHeader1));
    this.localSyncService
        .statusResponseEventProcessor()
        .process(waiting2, StatusResponse.create(statusHeader2));
    this.localSyncService
        .statusResponseEventProcessor()
        .process(waiting3, StatusResponse.create(statusHeader3));

    verify(syncRequestDispatcher, times(1)).dispatch(eq(waiting2), any());
  }

  @Test
  public void when_status_timeout_with_no_responses__then_should_reschedule_another_check() {
    final LedgerProof currentHeader = createHeaderAtStateVersion(10L);
    final BFTNode waiting1 = createPeer();
    setupPeersView(waiting1);

    final var syncState = SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(waiting1));
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .syncCheckReceiveStatusTimeoutEventProcessor()
        .process(SyncCheckReceiveStatusTimeout.create());

    verifyNoMoreInteractions(syncRequestDispatcher);
  }

  @Test
  public void when_status_timeout_with_at_least_one_response__then_should_start_sync() {
    final LedgerProof currentHeader = createHeaderAtStateVersion(10L);
    final LedgerProof statusHeader1 = createHeaderAtStateVersion(12L);
    final LedgerProof statusHeader2 = createHeaderAtStateVersion(20L);
    final var waiting1 = createPeer();
    final var waiting2 = createPeer();
    setupPeersView(waiting1, waiting2);

    final var syncState =
        SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(waiting1, waiting2));
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .statusResponseEventProcessor()
        .process(waiting1, StatusResponse.create(statusHeader1));

    this.localSyncService
        .syncCheckReceiveStatusTimeoutEventProcessor()
        .process(SyncCheckReceiveStatusTimeout.create());

    // even though statusHeader2 is more up to date, it should be ignored because was received
    // after a timeout event
    this.localSyncService
        .statusResponseEventProcessor()
        .process(waiting2, StatusResponse.create(statusHeader2));

    verify(syncRequestDispatcher, times(1)).dispatch(eq(waiting1), any());
  }

  @Test
  public void when_syncing_timeout__then_should_remove_candidate_and_retry_with_other_candidate() {
    final var currentHeader = createHeaderAtStateVersion(10L);
    final var targetHeader = createHeaderAtStateVersion(20L);

    final var peer1 = createPeer();
    final var peer2 = createPeer();
    setupPeersView(peer1, peer2);

    final var requestId = 1L;
    final var originalCandidates = ImmutableList.of(peer1, peer2);
    final var syncState =
        SyncState.SyncingState.init(currentHeader, originalCandidates, targetHeader)
            .withPendingRequest(peer1, requestId);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .syncRequestTimeoutEventProcessor()
        .process(SyncRequestTimeout.create(peer1, requestId));

    verify(syncRequestDispatcher, times(1)).dispatch(eq(peer2), any());
  }

  @Test
  public void when_syncing_timeout_for_different_peer_same_request_id__then_should_ignore() {
    final var currentHeader = createHeaderAtStateVersion(10L);
    final var targetHeader = createHeaderAtStateVersion(20L);

    final var peer1 = createPeer();
    final var peer2 = createPeer();
    setupPeersView(peer1, peer2);

    final var requestId = 1L;
    final var originalCandidates = ImmutableList.of(peer1, peer2);
    final var syncState =
        SyncState.SyncingState.init(currentHeader, originalCandidates, targetHeader)
            .withPendingRequest(peer1, requestId);
    this.setupSyncServiceWithState(syncState);

    // waiting for response from peer1, but got a timeout for peer2
    this.localSyncService
        .syncRequestTimeoutEventProcessor()
        .process(SyncRequestTimeout.create(peer2, requestId));

    verifyNoMoreInteractions(syncRequestDispatcher);
  }

  @Test
  public void when_syncing_timeout_for_same_peer_different_request_id__then_should_ignore() {
    final var currentHeader = createHeaderAtStateVersion(10L);
    final var targetHeader = createHeaderAtStateVersion(20L);

    final var peer1 = mock(BFTNode.class);
    final var peer2 = mock(BFTNode.class);
    when(peersView.peers()).thenAnswer(i -> Stream.of(peer1, peer2));

    final var originalCandidates = ImmutableList.of(peer1, peer2);
    final var syncState =
        SyncState.SyncingState.init(currentHeader, originalCandidates, targetHeader)
            .withPendingRequest(peer1, 2L);
    this.setupSyncServiceWithState(syncState);

    // waiting for response for request id 2, but got a timeout for 1
    this.localSyncService
        .syncRequestTimeoutEventProcessor()
        .process(SyncRequestTimeout.create(peer1, 1L));

    verifyNoMoreInteractions(syncRequestDispatcher);
  }

  @Test
  public void when_received_a_valid_response__then_should_send_verified() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(20L);

    final var peer1 = createPeer();
    setupPeersView(peer1);

    final var syncState =
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(peer1), targetHeader)
            .withPendingRequest(peer1, 1L);
    this.setupSyncServiceWithState(syncState);

    final var syncResponse = createValidMockedSyncResponse();

    this.localSyncService.syncResponseEventProcessor().process(peer1, syncResponse);

    verify(verifiedSyncResponseHandler, times(1)).handleVerifiedSyncResponse(syncResponse);
    verify(syncLedgerUpdateTimeoutDispatcher, times(1)).dispatch(any(), anyLong());
    verifyNoMoreInteractions(syncRequestDispatcher);
  }

  @Test
  public void
      when_received_ledger_update_and_fully_synced__then_should_wait_for_another_sync_trigger() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(20L);

    final var peer1 = createPeer();
    setupPeersView(peer1);

    final var syncState =
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(peer1), targetHeader)
            .withPendingRequest(peer1, 1L);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .ledgerUpdateEventProcessor()
        .process(ledgerUpdateAtStateVersion(targetHeader.getStateVersion()));

    verifyNoMoreInteractions(syncRequestDispatcher);
  }

  @Test
  public void when_ledger_update_timeout__then_should_continue_sync() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(21L);

    final var peer1 = createPeer();
    setupPeersView(peer1);

    final var syncState =
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(peer1), targetHeader);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .syncLedgerUpdateTimeoutProcessor()
        .process(SyncLedgerUpdateTimeout.create(currentHeader.getStateVersion()));

    verify(syncRequestDispatcher, times(1)).dispatch(eq(peer1), any());
  }

  @Test
  public void when_obsolete_ledger_update_timeout__then_should_ignore() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(21L);

    final var peer1 = createPeer();
    setupPeersView(peer1);

    final var syncState =
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(peer1), targetHeader);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .syncLedgerUpdateTimeoutProcessor()
        .process(
            SyncLedgerUpdateTimeout.create(
                currentHeader.getStateVersion() - 1) // timeout event for a past state version
            );

    verifyNoInteractions(syncRequestDispatcher);
  }

  @Test
  public void when_remote_status_update_in_idle__then_should_start_sync() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(21L);

    final var peer1 = createPeer();
    setupPeersView(peer1);

    final var syncState = SyncState.IdleState.init(currentHeader);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .ledgerStatusUpdateEventProcessor()
        .process(peer1, LedgerStatusUpdate.create(targetHeader));

    verify(syncRequestDispatcher, times(1)).dispatch(eq(peer1), any());
  }

  @Test
  public void when_remote_status_update_in_syncing__then_should_update_target() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(21L);
    final var newTargetHeader = createHeaderAtStateVersion(22L);

    final var peer1 = createPeer();
    final var peer2 = createPeer();
    setupPeersView(peer1, peer2);

    final var syncState =
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(peer1), targetHeader)
            .withPendingRequest(peer1, 1L);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .ledgerStatusUpdateEventProcessor()
        .process(peer2, LedgerStatusUpdate.create(newTargetHeader));

    assertEquals(
        newTargetHeader,
        ((SyncState.SyncingState) this.localSyncService.getSyncState()).getTargetHeader());
  }

  @Test
  public void when_remote_status_update_in_syncing_for_older_header__then_should_do_nothing() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(21L);
    final var newTargetHeader = createHeaderAtStateVersion(20L);

    final var peer1 = createPeer();
    final var peer2 = createPeer();
    setupPeersView(peer1, peer2);

    final var syncState =
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(peer1), targetHeader)
            .withPendingRequest(peer1, 1L);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .ledgerStatusUpdateEventProcessor()
        .process(peer2, LedgerStatusUpdate.create(newTargetHeader));

    assertEquals(syncState, this.localSyncService.getSyncState());
  }

  @Test
  public void when_ledger_status_update__then_should_not_add_duplicate_candidate() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(21L);
    final var newTargetHeader = createHeaderAtStateVersion(22L);
    final var evenNewerTargetHeader = createHeaderAtStateVersion(23L);

    final var peer1 = mock(BFTNode.class);
    final var peer2 = mock(BFTNode.class);
    final var peer3 = mock(BFTNode.class);
    setupPeersView(peer1, peer2, peer3);

    final var syncState =
        SyncState.SyncingState.init(currentHeader, ImmutableList.of(peer1, peer2), targetHeader)
            .withPendingRequest(peer1, 1L);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService
        .ledgerStatusUpdateEventProcessor()
        .process(peer3, LedgerStatusUpdate.create(newTargetHeader));

    // another, newer, ledger update from the same peer
    this.localSyncService
        .ledgerStatusUpdateEventProcessor()
        .process(peer3, LedgerStatusUpdate.create(evenNewerTargetHeader));

    assertEquals(
        peer3,
        ((SyncState.SyncingState) this.localSyncService.getSyncState()).peekNthCandidate(0).get());
    assertEquals(
        peer1,
        ((SyncState.SyncingState) this.localSyncService.getSyncState()).peekNthCandidate(1).get());
    assertEquals(
        peer2,
        ((SyncState.SyncingState) this.localSyncService.getSyncState()).peekNthCandidate(2).get());
    assertEquals(
        peer3,
        ((SyncState.SyncingState) this.localSyncService.getSyncState()).peekNthCandidate(3).get());
    assertEquals(
        peer1,
        ((SyncState.SyncingState) this.localSyncService.getSyncState()).peekNthCandidate(4).get());
    assertEquals(
        peer2,
        ((SyncState.SyncingState) this.localSyncService.getSyncState()).peekNthCandidate(5).get());
  }

  @Test
  public void when_syncing__then_should_use_round_robin_peers() {
    final var currentHeader = createHeaderAtStateVersion(19L);
    final var targetHeader = createHeaderAtStateVersion(30L);

    final var peer1 = createPeer();
    final var peer2 = createPeer();
    final var peer3 = createPeer();
    setupPeersView(peer1, peer2, peer3);

    final var syncState =
        SyncState.SyncingState.init(
            currentHeader, ImmutableList.of(peer1, peer2, peer3), targetHeader);
    this.setupSyncServiceWithState(syncState);

    this.localSyncService.ledgerUpdateEventProcessor().process(ledgerUpdateAtStateVersion(20L));
    verify(syncRequestDispatcher, times(1)).dispatch(eq(peer1), any());
    this.localSyncService
        .syncResponseEventProcessor()
        .process(peer1, createValidMockedSyncResponse());
    this.localSyncService.ledgerUpdateEventProcessor().process(ledgerUpdateAtStateVersion(21L));
    verify(syncRequestDispatcher, times(1)).dispatch(eq(peer2), any());
    this.localSyncService
        .syncResponseEventProcessor()
        .process(peer2, createValidMockedSyncResponse());
    this.localSyncService.ledgerUpdateEventProcessor().process(ledgerUpdateAtStateVersion(22L));
    verify(syncRequestDispatcher, times(1)).dispatch(eq(peer3), any());
    this.localSyncService
        .syncResponseEventProcessor()
        .process(peer3, createValidMockedSyncResponse());
    this.localSyncService.ledgerUpdateEventProcessor().process(ledgerUpdateAtStateVersion(23L));
    verify(syncRequestDispatcher, times(2)).dispatch(eq(peer1), any());
  }

  private SyncResponse createValidMockedSyncResponse() {
    final var respHeadLedgerHeader = mock(LedgerHeader.class);
    final var respHeadAccumulatorState = mock(AccumulatorState.class);
    when(respHeadLedgerHeader.getAccumulatorState()).thenReturn(respHeadAccumulatorState);
    final var respTailLedgerHeader = mock(LedgerHeader.class);
    final var respTailAccumulatorState = mock(AccumulatorState.class);
    when(respTailLedgerHeader.getAccumulatorState()).thenReturn(respTailAccumulatorState);
    final var respHead = mock(DtoLedgerProof.class);
    when(respHead.getLedgerHeader()).thenReturn(respHeadLedgerHeader);
    final var respTail = mock(DtoLedgerProof.class);
    when(respTail.getLedgerHeader()).thenReturn(respTailLedgerHeader);
    final var response = mock(DtoTxnsAndProof.class);
    final var txn = mock(Txn.class);
    when(txn.getId()).thenReturn(AID.ZERO);
    when(response.getTxns()).thenReturn(ImmutableList.of(txn));
    when(response.getHead()).thenReturn(respHead);
    when(response.getTail()).thenReturn(respTail);

    final var syncResponse = SyncResponse.create(response);

    when(validatorSetVerifier.verifyValidatorSet(syncResponse)).thenReturn(true);
    when(signaturesVerifier.verifyResponseSignatures(syncResponse)).thenReturn(true);
    when(accumulatorVerifier.verify(
            eq(respHeadAccumulatorState), any(), eq(respTailAccumulatorState)))
        .thenReturn(true);

    return syncResponse;
  }

  private LedgerUpdate ledgerUpdateAtStateVersion(long stateVersion) {
    return new LedgerUpdate(
        VerifiedTxnsAndProof.create(ImmutableList.of(), createHeaderAtStateVersion(stateVersion)),
        ImmutableClassToInstanceMap.of());
  }

  private LedgerProof createHeaderAtStateVersion(long version) {
    final LedgerProof header = mock(LedgerProof.class);
    final AccumulatorState accumulatorState = mock(AccumulatorState.class);
    when(header.getAccumulatorState()).thenReturn(accumulatorState);
    when(accumulatorState.getStateVersion()).thenReturn(version);
    return header;
  }

  private void setupPeersView(BFTNode... bftNodes) {
    when(peersView.peers()).thenReturn(Stream.of(bftNodes).map(PeerInfo::fromBftNode));
    Arrays.stream(bftNodes).forEach(peer -> when(peersView.hasPeer(peer)).thenReturn(true));
  }

  private BFTNode createPeer() {
    return BFTNode.random();
  }
}
