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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.network.addressbook.AddressBook;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import com.radixdlt.sync.validation.RemoteSyncResponseSignaturesVerifier;
import com.radixdlt.sync.validation.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.utils.RandomHasher;
import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

public class LocalSyncServiceTest {

	private LocalSyncService localSyncService;

	private EventDispatcher<SyncCheckTrigger> syncCheckTriggerDispatcher;
	private RemoteEventDispatcher<StatusRequest> statusRequestDispatcher;
	private ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutDispatcher;
	private RemoteEventDispatcher<SyncRequest> syncRequestDispatcher;
	private ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher;
	private SyncConfig syncConfig;
	private SystemCounters systemCounters;
	private AddressBook addressBook;
	private Comparator<AccumulatorState> accComparator;
	private Hasher hasher;
	private RemoteSyncResponseValidatorSetVerifier validatorSetVerifier;
	private RemoteSyncResponseSignaturesVerifier signaturesVerifier;
	private LedgerAccumulatorVerifier accumulatorVerifier;
	private LocalSyncService.VerifiedSyncResponseSender verifiedSender;
	private LocalSyncService.InvalidSyncResponseSender invalidSyncedCommandsSender;

	@Before
	public void setUp() {
		this.syncCheckTriggerDispatcher = rmock(ScheduledEventDispatcher.class);
		this.statusRequestDispatcher = rmock(RemoteEventDispatcher.class);
		this.syncCheckReceiveStatusTimeoutDispatcher = rmock(ScheduledEventDispatcher.class);
		this.syncRequestDispatcher = rmock(RemoteEventDispatcher.class);
		this.syncRequestTimeoutDispatcher = rmock(ScheduledEventDispatcher.class);
		this.syncConfig = SyncConfig.of(1000L, 10, 10000L);
		this.systemCounters = mock(SystemCounters.class);
		this.addressBook = mock(AddressBook.class);
		this.accComparator = Comparator.comparingLong(AccumulatorState::getStateVersion);
		this.hasher = new RandomHasher();
		this.validatorSetVerifier = mock(RemoteSyncResponseValidatorSetVerifier.class);
		this.signaturesVerifier = mock(RemoteSyncResponseSignaturesVerifier.class);
		this.accumulatorVerifier = mock(LedgerAccumulatorVerifier.class);
		this.verifiedSender = mock(LocalSyncService.VerifiedSyncResponseSender.class);
		this.invalidSyncedCommandsSender = mock(LocalSyncService.InvalidSyncResponseSender.class);
	}

	private void setupSyncServiceWithState(SyncState syncState) {
		this.localSyncService = new LocalSyncService(
			syncCheckTriggerDispatcher,
			statusRequestDispatcher,
			syncCheckReceiveStatusTimeoutDispatcher,
			syncRequestDispatcher,
			syncRequestTimeoutDispatcher,
			syncConfig,
			systemCounters,
			addressBook,
			accComparator,
			hasher,
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

		when(addressBook.peers()).thenReturn(Stream.of(peer1, peer2, peer3));

		final VerifiedLedgerHeaderAndProof currentHeader = mock(VerifiedLedgerHeaderAndProof.class);
		this.setupSyncServiceWithState(SyncState.IdleState.init(currentHeader));

		this.localSyncService.syncCheckTriggerEventProcessor().process(SyncCheckTrigger.create());

		verify(statusRequestDispatcher, times(1)).dispatch(eq(BFTNode.create(peer1.getSystem().getKey())), any());
		verify(statusRequestDispatcher, times(1)).dispatch(eq(BFTNode.create(peer2.getSystem().getKey())), any());
		verify(statusRequestDispatcher, times(1)).dispatch(eq(BFTNode.create(peer3.getSystem().getKey())), any());
	}

	@Test
	public void when_sync_check_is_triggered_at_non_idle__then_should_be_ignored() {
		final VerifiedLedgerHeaderAndProof currentHeader = mock(VerifiedLedgerHeaderAndProof.class);

		this.setupSyncServiceWithState(SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of()));
		this.localSyncService.syncCheckTriggerEventProcessor().process(SyncCheckTrigger.create());

		this.setupSyncServiceWithState(SyncState.SyncingState.init(currentHeader, ImmutableList.of(), currentHeader));
		this.localSyncService.syncCheckTriggerEventProcessor().process(SyncCheckTrigger.create());

		verifyNoMoreInteractions(addressBook);
		verifyNoMoreInteractions(statusRequestDispatcher);
	}

	@Test
	public void when_status_response_received_at_non_sync_check__then_should_be_ignored() {
		final VerifiedLedgerHeaderAndProof currentHeader = mock(VerifiedLedgerHeaderAndProof.class);
		final VerifiedLedgerHeaderAndProof statusHeader = mock(VerifiedLedgerHeaderAndProof.class);
		final BFTNode sender = mock(BFTNode.class);

		this.setupSyncServiceWithState(SyncState.IdleState.init(currentHeader));
		this.localSyncService.statusResponseEventProcessor().process(sender, StatusResponse.create(statusHeader));

		this.setupSyncServiceWithState(SyncState.SyncingState.init(currentHeader, ImmutableList.of(), currentHeader));
		this.localSyncService.statusResponseEventProcessor().process(sender, StatusResponse.create(statusHeader));

		verifyNoMoreInteractions(addressBook);
		verifyNoMoreInteractions(statusRequestDispatcher);
		verifyNoMoreInteractions(syncRequestDispatcher);
		verifyNoMoreInteractions(syncRequestTimeoutDispatcher);
	}

	@Test
	public void when_unexpected_status_response_received__then_should_be_ignored() {
		final VerifiedLedgerHeaderAndProof currentHeader = mock(VerifiedLedgerHeaderAndProof.class);
		final VerifiedLedgerHeaderAndProof statusHeader = mock(VerifiedLedgerHeaderAndProof.class);
		final BFTNode expectedPeer = mock(BFTNode.class);
		final BFTNode unexpectedPeer = mock(BFTNode.class);

		this.setupSyncServiceWithState(SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(expectedPeer)));
		this.localSyncService.statusResponseEventProcessor().process(unexpectedPeer, StatusResponse.create(statusHeader));

		verifyNoMoreInteractions(addressBook);
		verifyNoMoreInteractions(statusRequestDispatcher);
		verifyNoMoreInteractions(syncRequestDispatcher);
		verifyNoMoreInteractions(syncRequestTimeoutDispatcher);
	}

	@Test
	public void when_duplicate_status_response_received__then_should_be_ignored() {
		final VerifiedLedgerHeaderAndProof currentHeader = mock(VerifiedLedgerHeaderAndProof.class);
		final VerifiedLedgerHeaderAndProof statusHeader = mock(VerifiedLedgerHeaderAndProof.class);
		final BFTNode expectedPeer = mock(BFTNode.class);
		final BFTNode alreadyReceivedPeer = mock(BFTNode.class);

		final var syncState =
			SyncState.SyncCheckState.init(currentHeader, ImmutableSet.of(expectedPeer))
				.withStatusResponse(alreadyReceivedPeer, StatusResponse.create(statusHeader));

		this.setupSyncServiceWithState(syncState);
		this.localSyncService.statusResponseEventProcessor().process(alreadyReceivedPeer, StatusResponse.create(statusHeader));

		verifyNoMoreInteractions(addressBook);
		verifyNoMoreInteractions(statusRequestDispatcher);
		verifyNoMoreInteractions(syncRequestDispatcher);
		verifyNoMoreInteractions(syncRequestTimeoutDispatcher);
	}

	@Test
	public void when_all_status_responses_received__then_should_start_sync() {
		final VerifiedLedgerHeaderAndProof currentHeader = createHeaderAtStateVersion(10L);
		final VerifiedLedgerHeaderAndProof statusHeader1 = createHeaderAtStateVersion(2L);
		final VerifiedLedgerHeaderAndProof statusHeader2 = createHeaderAtStateVersion(20L);
		final VerifiedLedgerHeaderAndProof statusHeader3 = createHeaderAtStateVersion(15L);
		final BFTNode waiting1 = mock(BFTNode.class);
		final BFTNode waiting2 = mock(BFTNode.class);
		final BFTNode waiting3 = mock(BFTNode.class);

		final var syncState = SyncState.SyncCheckState.init(
			currentHeader, ImmutableSet.of(waiting1, waiting2, waiting3));
		this.setupSyncServiceWithState(syncState);

		when(addressBook.hasBftNodePeer(waiting2)).thenReturn(true);

		this.localSyncService.statusResponseEventProcessor().process(waiting1, StatusResponse.create(statusHeader1));
		this.localSyncService.statusResponseEventProcessor().process(waiting2, StatusResponse.create(statusHeader2));
		this.localSyncService.statusResponseEventProcessor().process(waiting3, StatusResponse.create(statusHeader3));

		verify(syncRequestDispatcher, times(1)).dispatch(eq(waiting2), any());
	}

	@Test
	public void when_status_timeout_with_no_responses__then_should_become_idle() {
		final VerifiedLedgerHeaderAndProof currentHeader = createHeaderAtStateVersion(10L);
		final BFTNode waiting1 = mock(BFTNode.class);
		when(addressBook.hasBftNodePeer(waiting1)).thenReturn(true);

		final var syncState = SyncState.SyncCheckState.init(
			currentHeader, ImmutableSet.of(waiting1));
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.syncCheckReceiveStatusTimeoutEventProcessor().process(
			SyncCheckReceiveStatusTimeout.create()
		);

		verifyNoMoreInteractions(syncRequestDispatcher);
		// TODO(luk): fixme
//		verify(syncCheckTriggerDispatcher, times(1)).dispatch(any(), eq(syncConfig.syncCheckInterval()));
	}

	@Test
	public void when_status_timeout_with_at_least_one_response__then_should_start_sync() {
		final VerifiedLedgerHeaderAndProof currentHeader = createHeaderAtStateVersion(10L);
		final VerifiedLedgerHeaderAndProof statusHeader1 = createHeaderAtStateVersion(12L);
		final VerifiedLedgerHeaderAndProof statusHeader2 = createHeaderAtStateVersion(20L);
		final BFTNode waiting1 = mock(BFTNode.class);
		when(addressBook.hasBftNodePeer(waiting1)).thenReturn(true);
		final BFTNode waiting2 = mock(BFTNode.class);
		when(addressBook.hasBftNodePeer(waiting2)).thenReturn(true);

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
		verifyNoMoreInteractions(syncCheckTriggerDispatcher);
	}

	@Test
	public void when_syncing_timeout__then_should_remove_candidate_and_retry_with_other_candidate() {
		final var currentHeader = createHeaderAtStateVersion(10L);
		final var targetHeader = createHeaderAtStateVersion(20L);

		final var peer1 = mock(BFTNode.class);
		when(addressBook.hasBftNodePeer(eq(peer1))).thenReturn(true);
		final var peer2 = mock(BFTNode.class);
		when(addressBook.hasBftNodePeer(eq(peer2))).thenReturn(true);

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
		when(addressBook.hasBftNodePeer(eq(peer1))).thenReturn(true);
		final var peer2 = mock(BFTNode.class);
		when(addressBook.hasBftNodePeer(eq(peer2))).thenReturn(true);

		final var originalCandidates = ImmutableList.of(peer1, peer2);
		final var syncState = SyncState.SyncingState.init(
			currentHeader, originalCandidates, targetHeader).withWaitingFor(peer1);
		this.setupSyncServiceWithState(syncState);

		// waiting for response from peer1, but got a timeout for peer2
		this.localSyncService.syncRequestTimeoutEventProcessor()
			.process(SyncRequestTimeout.create(peer2, currentHeader));

		verifyNoMoreInteractions(syncCheckTriggerDispatcher);
		verifyNoMoreInteractions(syncRequestDispatcher);
	}

	@Test
	public void when_received_a_valid_response__then_should_send_verified() {
		final var currentHeader = createHeaderAtStateVersion(19L);
		final var targetHeader = createHeaderAtStateVersion(20L);

		final var peer1 = mock(BFTNode.class);
		when(addressBook.hasBftNodePeer(eq(peer1))).thenReturn(true);

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
		when(response.getCommands()).thenReturn(ImmutableList.of(cmd));
		when(response.getHead()).thenReturn(respHead);
		when(response.getTail()).thenReturn(respTail);

		final var syncResponse = SyncResponse.create(response);

		when(validatorSetVerifier.verifyValidatorSet(syncResponse)).thenReturn(true);
		when(signaturesVerifier.verifyResponseSignatures(syncResponse)).thenReturn(true);
		when(accumulatorVerifier.verify(any(), any(), any())).thenReturn(true);

		this.localSyncService.syncResponseEventProcessor().process(peer1, syncResponse);

		verify(verifiedSender, times(1)).sendVerifiedSyncResponse(syncResponse);
		verifyNoMoreInteractions(syncRequestDispatcher);
	}

	@Test
	public void when_received_ledger_update_and_fully_synced__then_should_schedule_sync_check() {
		final var currentHeader = createHeaderAtStateVersion(19L);
		final var targetHeader = createHeaderAtStateVersion(20L);

		final var peer1 = mock(BFTNode.class);
		when(addressBook.hasBftNodePeer(eq(peer1))).thenReturn(true);

		final var syncState = SyncState.SyncingState.init(
				currentHeader, ImmutableList.of(peer1), targetHeader).withWaitingFor(peer1);
		this.setupSyncServiceWithState(syncState);

		this.localSyncService.ledgerUpdateEventProcessor().process(new LedgerUpdate() {
			@Override
			public ImmutableList<Command> getNewCommands() {
				return ImmutableList.of();
			}

			@Override
			public VerifiedLedgerHeaderAndProof getTail() {
				return targetHeader;
			}

			@Override
			public Optional<BFTValidatorSet> getNextValidatorSet() {
				return Optional.empty();
			}
		});

		// TODO(luk): fixme
//		verify(syncCheckTriggerDispatcher, times(1)).dispatch(any(), eq(syncConfig.syncCheckInterval()));
		verifyNoMoreInteractions(syncRequestDispatcher);
	}

	private VerifiedLedgerHeaderAndProof createHeaderAtStateVersion(long version) {
		final VerifiedLedgerHeaderAndProof header = mock(VerifiedLedgerHeaderAndProof.class);
		final AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(header.getAccumulatorState()).thenReturn(accumulatorState);
		when(accumulatorState.getStateVersion()).thenReturn(version);
		return header;
	}

	private PeerWithSystem createPeer() {
		final var peer = mock(PeerWithSystem.class);
		final var system = mock(RadixSystem.class);
		when(peer.getSystem()).thenReturn(system);
		when(system.getKey()).thenReturn(ECKeyPair.generateNew().getPublicKey());
		return peer;
	}
}
