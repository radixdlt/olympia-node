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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyLong;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedCommandsAndProof;

import java.util.Comparator;
import java.util.List;

import com.radixdlt.network.addressbook.PeersView;
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
import org.junit.Before;
import org.junit.Test;

public class LocalSyncServiceTest {

	private LocalSyncService localSyncService;

	private RemoteEventDispatcher<StatusRequest> statusRequestDispatcher;
	private ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutDispatcher;
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
	private LocalSyncService.VerifiedSyncResponseSender verifiedSender;
	private LocalSyncService.InvalidSyncResponseSender invalidSyncedCommandsSender;

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
		this.verifiedSender = mock(LocalSyncService.VerifiedSyncResponseSender.class);
		this.invalidSyncedCommandsSender = mock(LocalSyncService.InvalidSyncResponseSender.class);
	}

	private void setupSyncServiceWithState(SyncState syncState) {
		this.localSyncService = new LocalSyncService(
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
			verifiedSender,
			invalidSyncedCommandsSender,
			syncState
		);
	}

	@Test
	public void when_sync_check_is_triggered_at_idle__then_should_ask_peers_for_their_statuses() {
		final var peer1 = createPeer();
		final var peer2 = createPeer();
		final var peer3 = createPeer();

		when(peersView.peers()).thenReturn(List.of(peer1, peer2, peer3));

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

		this.setupSyncServiceWithState(SyncState.SyncingState.init(currentHeader, ImmutableList.of(), currentHeader));
		this.localSyncService.syncCheckTriggerEventProcessor().process(SyncCheckTrigger.create());

		verifyNoMoreInteractions(peersView);
		verifyNoMoreInteractions(statusRequestDispatcher);
	}

	@Test
	public void when_status_response_received_at_non_sync_check__then_should_be_ignored() {
		final LedgerProof currentHeader = mock(LedgerProof.class);
		final LedgerProof statusHeader = mock(LedgerProof.class);
		final BFTNode sender = mock(BFTNode.class);

		this.setupSyncServiceWithState(SyncState.IdleState.init(currentHeader));
		this.localSyncService.statusResponseEventProcessor().process(sender, StatusResponse.create(statusHeader));

		this.setupSyncServiceWithState(SyncState.SyncingState.init(currentHeader, ImmutableList.of(), currentHeader));
		this.localSyncService.statusResponseEventProcessor().process(sender, StatusResponse.create(statusHeader));

		verifyNoMoreInteractions(peersView);
		verifyNoMoreInteractions(statusRequestDispatcher);
		verifyNoMoreInteractions(syncRequestDispatcher);
		verifyNoMoreInteractions(syncRequestTimeoutDispatcher);
	}

	@Test
	public void when_unexpected_status_response_received__then_should_be_ignored() {
		final LedgerProof currentHeader = mock(LedgerProof.class);
		final LedgerProof statusHeader = mock(LedgerProof.class);
		final BFTNode expectedPeer = mock(BFTNode.class);
		final BFTNode unexpectedPeer = mock(BFTNode.class);

		this.setupSyncServiceWithState(SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(expectedPeer)));
		this.localSyncService.statusResponseEventProcessor().process(unexpectedPeer, StatusResponse.create(statusHeader));

		verifyNoMoreInteractions(peersView);
		verifyNoMoreInteractions(statusRequestDispatcher);
		verifyNoMoreInteractions(syncRequestDispatcher);
		verifyNoMoreInteractions(syncRequestTimeoutDispatcher);
	}

	@Test
	public void when_duplicate_status_response_received__then_should_be_ignored() {
		final LedgerProof currentHeader = mock(LedgerProof.class);
		final LedgerProof statusHeader = mock(LedgerProof.class);
		final BFTNode expectedPeer = mock(BFTNode.class);
		final BFTNode alreadyReceivedPeer = mock(BFTNode.class);

		final var syncState =
			SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(expectedPeer))
				.withStatusResponse(alreadyReceivedPeer, StatusResponse.create(statusHeader));

		this.setupSyncServiceWithState(syncState);
		this.localSyncService.statusResponseEventProcessor().process(alreadyReceivedPeer, StatusResponse.create(statusHeader));

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
		final BFTNode waiting1 = mock(BFTNode.class);
		final BFTNode waiting2 = mock(BFTNode.class);
		final BFTNode waiting3 = mock(BFTNode.class);

		final var syncState = SyncState.SyncCheckState.init(
			currentHeader, ImmutableSet.of(waiting1, waiting2, waiting3));
		this.setupSyncServiceWithState(syncState);

		when(peersView.peers()).thenReturn(List.of(waiting2));

		this.localSyncService.statusResponseEventProcessor().process(waiting1, StatusResponse.create(statusHeader1));
		this.localSyncService.statusResponseEventProcessor().process(waiting2, StatusResponse.create(statusHeader2));
		this.localSyncService.statusResponseEventProcessor().process(waiting3, StatusResponse.create(statusHeader3));

		verify(syncRequestDispatcher, times(1)).dispatch(eq(waiting2), any());
	}

	@Test
	public void when_status_timeout_with_no_responses__then_should_reschedule_another_check() {
		final LedgerProof currentHeader = createHeaderAtStateVersion(10L);
		final BFTNode waiting1 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(waiting1));

		final var syncState = SyncState.SyncCheckState.init(
			currentHeader, ImmutableSet.of(waiting1));
		this.setupSyncServiceWithState(syncState);


		this.localSyncService.syncCheckReceiveStatusTimeoutEventProcessor().process(
			SyncCheckReceiveStatusTimeout.create()
		);

		verifyNoMoreInteractions(syncRequestDispatcher);
	}

	@Test
	public void when_status_timeout_with_at_least_one_response__then_should_start_sync() {
		final LedgerProof currentHeader = createHeaderAtStateVersion(10L);
		final LedgerProof statusHeader1 = createHeaderAtStateVersion(12L);
		final LedgerProof statusHeader2 = createHeaderAtStateVersion(20L);
		final BFTNode waiting1 = mock(BFTNode.class);
		final BFTNode waiting2 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(waiting1, waiting2));

		final var syncState = SyncState.SyncCheckState.init(
			currentHeader, ImmutableSet.of(waiting1, waiting2));
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.statusResponseEventProcessor().process(waiting1, StatusResponse.create(statusHeader1));

		this.localSyncService.syncCheckReceiveStatusTimeoutEventProcessor().process(
			SyncCheckReceiveStatusTimeout.create()
		);

		// even though statusHeader2 is more up to date, it should be ignored because was received
		// after a timeout event
		this.localSyncService.statusResponseEventProcessor().process(waiting2, StatusResponse.create(statusHeader2));

		verify(syncRequestDispatcher, times(1)).dispatch(eq(waiting1), any());
	}

	@Test
	public void when_syncing_timeout__then_should_remove_candidate_and_retry_with_other_candidate() {
		final var currentHeader = createHeaderAtStateVersion(10L);
		final var targetHeader = createHeaderAtStateVersion(20L);

		final var peer1 = mock(BFTNode.class);
		final var peer2 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(peer1, peer2));

		final var originalCandidates = ImmutableList.of(peer1, peer2);
		final var syncState = SyncState.SyncingState.init(
			currentHeader, originalCandidates, targetHeader).withWaitingFor(peer1);
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.syncRequestTimeoutEventProcessor()
			.process(SyncRequestTimeout.create(peer1, currentHeader));

		verify(syncRequestDispatcher, times(1)).dispatch(eq(peer2), any());
	}

	@Test
	public void when_syncing_timeout_for_unexpected_peer__then_should_ignore() {
		final var currentHeader = createHeaderAtStateVersion(10L);
		final var targetHeader = createHeaderAtStateVersion(20L);

		final var peer1 = mock(BFTNode.class);
		final var peer2 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(peer1, peer2));

		final var originalCandidates = ImmutableList.of(peer1, peer2);
		final var syncState = SyncState.SyncingState.init(
			currentHeader, originalCandidates, targetHeader).withWaitingFor(peer1);
		this.setupSyncServiceWithState(syncState);

		// waiting for response from peer1, but got a timeout for peer2
		this.localSyncService.syncRequestTimeoutEventProcessor()
			.process(SyncRequestTimeout.create(peer2, currentHeader));

		verifyNoMoreInteractions(syncRequestDispatcher);
	}

	@Test
	public void when_received_a_valid_response__then_should_send_verified() {
		final var currentHeader = createHeaderAtStateVersion(19L);
		final var targetHeader = createHeaderAtStateVersion(20L);

		final var peer1 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(peer1));

		final var syncState = SyncState.SyncingState.init(
			currentHeader, ImmutableList.of(peer1), targetHeader).withWaitingFor(peer1);
		this.setupSyncServiceWithState(syncState);

		final var respHeadLedgerHeader = mock(LedgerHeader.class);
		when(respHeadLedgerHeader.getAccumulatorState()).thenReturn(mock(AccumulatorState.class));
		final var respTailLedgerHeader = mock(LedgerHeader.class);
		when(respTailLedgerHeader.getAccumulatorState()).thenReturn(mock(AccumulatorState.class));
		final var respHead = mock(DtoLedgerHeaderAndProof.class);
		when(respHead.getLedgerHeader()).thenReturn(respHeadLedgerHeader);
		final var respTail = mock(DtoLedgerHeaderAndProof.class);
		when(respTail.getLedgerHeader()).thenReturn(respTailLedgerHeader);
		final var response = mock(DtoCommandsAndProof.class);
		final var cmd = mock(Command.class);
		when(cmd.getId()).thenReturn(AID.ZERO);
		when(response.getCommands()).thenReturn(ImmutableList.of(cmd));
		when(response.getHead()).thenReturn(respHead);
		when(response.getTail()).thenReturn(respTail);

		final var syncResponse = SyncResponse.create(response);

		when(validatorSetVerifier.verifyValidatorSet(syncResponse)).thenReturn(true);
		when(signaturesVerifier.verifyResponseSignatures(syncResponse)).thenReturn(true);
		when(accumulatorVerifier.verify(any(), any(), any())).thenReturn(true);

		this.localSyncService.syncResponseEventProcessor().process(peer1, syncResponse);

		verify(verifiedSender, times(1)).sendVerifiedSyncResponse(syncResponse);
		verify(syncLedgerUpdateTimeoutDispatcher, times(1)).dispatch(any(), anyLong());
		verifyNoMoreInteractions(syncRequestDispatcher);
	}

	@Test
	public void when_received_ledger_update_and_fully_synced__then_should_wait_for_another_sync_trigger() {
		final var currentHeader = createHeaderAtStateVersion(19L);
		final var targetHeader = createHeaderAtStateVersion(20L);

		final var peer1 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(peer1));

		final var syncState = SyncState.SyncingState.init(
				currentHeader, ImmutableList.of(peer1), targetHeader).withWaitingFor(peer1);
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.ledgerUpdateEventProcessor().process(
			new LedgerUpdate(new VerifiedCommandsAndProof(ImmutableList.of(), targetHeader))
		);

		verifyNoMoreInteractions(syncRequestDispatcher);
	}

	@Test
	public void when_ledger_update_timeout__then_should_continue_sync() {
		final var currentHeader = createHeaderAtStateVersion(19L);
		final var targetHeader = createHeaderAtStateVersion(21L);

		final var peer1 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(peer1));

		final var syncState = SyncState.SyncingState.init(
			currentHeader, ImmutableList.of(peer1), targetHeader);
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.syncLedgerUpdateTimeoutProcessor().process(
			SyncLedgerUpdateTimeout.create()
		);

		verify(syncRequestDispatcher, times(1)).dispatch(eq(peer1), any());
	}

	@Test
	public void when_remote_status_update_in_idle__then_should_start_sync() {
		final var currentHeader = createHeaderAtStateVersion(19L);
		final var targetHeader = createHeaderAtStateVersion(21L);

		final var peer1 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(peer1));

		final var syncState = SyncState.IdleState.init(currentHeader);
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.ledgerStatusUpdateEventProcessor().process(
			peer1,
			LedgerStatusUpdate.create(targetHeader)
		);

		verify(syncRequestDispatcher, times(1)).dispatch(eq(peer1), any());
	}

	@Test
	public void when_remote_status_update_in_syncing__then_should_update_target() {
		final var currentHeader = createHeaderAtStateVersion(19L);
		final var targetHeader = createHeaderAtStateVersion(21L);
		final var newTargetHeader = createHeaderAtStateVersion(22L);

		final var peer1 = mock(BFTNode.class);
		final var peer2 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(peer1, peer2));

		final var syncState = SyncState.SyncingState.init(
			currentHeader, ImmutableList.of(peer1), targetHeader).withWaitingFor(peer1);
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.ledgerStatusUpdateEventProcessor().process(
			peer2,
			LedgerStatusUpdate.create(newTargetHeader)
		);

		assertEquals(
			newTargetHeader,
			((SyncState.SyncingState) this.localSyncService.getSyncState()).getTargetHeader()
		);
	}

	@Test
	public void when_remote_status_update_in_syncing_for_older_header__then_should_do_nothing() {
		final var currentHeader = createHeaderAtStateVersion(19L);
		final var targetHeader = createHeaderAtStateVersion(21L);
		final var newTargetHeader = createHeaderAtStateVersion(20L);

		final var peer1 = mock(BFTNode.class);
		final var peer2 = mock(BFTNode.class);
		when(peersView.peers()).thenReturn(List.of(peer1, peer2));

		final var syncState = SyncState.SyncingState.init(
			currentHeader, ImmutableList.of(peer1), targetHeader).withWaitingFor(peer1);
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.ledgerStatusUpdateEventProcessor().process(
			peer2,
			LedgerStatusUpdate.create(newTargetHeader)
		);

		assertEquals(syncState, this.localSyncService.getSyncState());
	}

	private LedgerProof createHeaderAtStateVersion(long version) {
		final LedgerProof header = mock(LedgerProof.class);
		final AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(header.getAccumulatorState()).thenReturn(accumulatorState);
		when(accumulatorState.getStateVersion()).thenReturn(version);
		return header;
	}

	private BFTNode createPeer() {
		return BFTNode.random();
	}
}
