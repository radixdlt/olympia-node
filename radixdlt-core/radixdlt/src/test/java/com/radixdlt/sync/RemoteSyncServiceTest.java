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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.store.NextCommittedLimitReachedException;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

public class RemoteSyncServiceTest {

	private RemoteSyncService processor;
	private AddressBook addressBook;
	private LocalSyncService localSyncService;
	private CommittedReader reader;
	private RemoteEventDispatcher<StatusResponse> statusResponseDispatcher;
	private RemoteEventDispatcher<SyncResponse> syncResponseDispatcher;
	private RemoteEventDispatcher<LedgerStatusUpdate> statusUpdateDispatcher;

	@Before
	public void setUp() {
		this.addressBook = mock(AddressBook.class);
		this.localSyncService = mock(LocalSyncService.class);
		this.reader = mock(CommittedReader.class);
		this.statusResponseDispatcher =  rmock(RemoteEventDispatcher.class);
		this.syncResponseDispatcher =  rmock(RemoteEventDispatcher.class);
		this.statusUpdateDispatcher =  rmock(RemoteEventDispatcher.class);

		final var initialHeader = mock(VerifiedLedgerHeaderAndProof.class);
		final var initialAccumulatorState = mock(AccumulatorState.class);
		when(initialHeader.getAccumulatorState()).thenReturn(initialAccumulatorState);
		when(initialAccumulatorState.getStateVersion()).thenReturn(1L);

		this.processor = new RemoteSyncService(
			addressBook,
			localSyncService,
			reader,
			statusResponseDispatcher,
			syncResponseDispatcher,
			statusUpdateDispatcher,
			SyncConfig.of(5000L, 10, 5000L, 1, 10, 50),
			mock(SystemCounters.class),
			Comparator.comparingLong(AccumulatorState::getStateVersion),
			initialHeader,
			mock(BFTValidatorSet.class));
	}

	@Test
	public void when_remote_sync_request__then_process_it() throws NextCommittedLimitReachedException {
		SyncRequest request = mock(SyncRequest.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(HashCode.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getHeader()).thenReturn(header);
		BFTNode node = mock(BFTNode.class);
		VerifiedCommandsAndProof verifiedCommandsAndProof = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerHeaderAndProof verifiedHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(verifiedHeader.toDto()).thenReturn(header);
		when(verifiedCommandsAndProof.getHeader()).thenReturn(verifiedHeader);
		when(reader.getNextCommittedCommands(any(), anyInt())).thenReturn(verifiedCommandsAndProof);
		processor.syncRequestEventProcessor().process(node, SyncRequest.create(header));
		verify(syncResponseDispatcher, times(1)).dispatch(eq(node), any());
	}

	@Test
	public void when_remote_sync_request_and_unable__then_dont_do_anything() {
		SyncRequest request = mock(SyncRequest.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(HashCode.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getHeader()).thenReturn(header);
		processor.syncRequestEventProcessor().process(BFTNode.random(), SyncRequest.create(header));
		verify(syncResponseDispatcher, never()).dispatch(any(BFTNode.class), any());
	}

	@Test
	public void when_remote_sync_request_and_null_return__then_dont_do_anything() throws NextCommittedLimitReachedException {
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(HashCode.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		processor.syncRequestEventProcessor().process(BFTNode.random(), SyncRequest.create(header));
		when(reader.getNextCommittedCommands(any(), anyInt())).thenReturn(null);
		verify(syncResponseDispatcher, never()).dispatch(any(BFTNode.class), any());
	}

	@Test
	public void when_ledger_update__then_send_status_update_to_non_validator_peers() {
		final var tail = mock(VerifiedLedgerHeaderAndProof.class);
		final var ledgerUpdate = mock(LedgerUpdate.class);
		final var accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(2L);
		when(tail.getAccumulatorState()).thenReturn(accumulatorState);
		when(ledgerUpdate.getTail()).thenReturn(tail);

		final var validatorSet = mock(BFTValidatorSet.class);
		when(ledgerUpdate.getNextValidatorSet()).thenReturn(Optional.of(validatorSet));

		when(this.localSyncService.getSyncState())
			.thenReturn(SyncState.IdleState.init(mock(VerifiedLedgerHeaderAndProof.class)));

		final var peer1 = createPeer();
		final var peer1BftNode = BFTNode.create(peer1.getSystem().getKey());
		when(validatorSet.containsNode(peer1BftNode)).thenReturn(true);

		final var peer2 = createPeer();
		final var peer2BftNode = BFTNode.create(peer2.getSystem().getKey());
		when(validatorSet.containsNode(peer2BftNode)).thenReturn(false);

		when(this.addressBook.peers()).thenReturn(Stream.of(peer1, peer2));

		processor.ledgerUpdateEventProcessor().process(ledgerUpdate);

		verify(statusUpdateDispatcher, times(1)).dispatch(eq(peer2BftNode), eq(LedgerStatusUpdate.create(tail)));
		verifyNoMoreInteractions(statusUpdateDispatcher);
	}

	private PeerWithSystem createPeer() {
		final var peer = mock(PeerWithSystem.class);
		final var system = mock(RadixSystem.class);
		when(system.getKey()).thenReturn(ECKeyPair.generateNew().getPublicKey());
		when(peer.getSystem()).thenReturn(system);
		return peer;
	}
}
