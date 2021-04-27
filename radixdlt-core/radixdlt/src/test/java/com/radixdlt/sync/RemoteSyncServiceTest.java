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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;

import java.util.Comparator;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class RemoteSyncServiceTest {

	private RemoteSyncService processor;
	private PeersView peersView;
	private LocalSyncService localSyncService;
	private CommittedReader reader;
	private RemoteEventDispatcher<StatusResponse> statusResponseDispatcher;
	private RemoteEventDispatcher<SyncResponse> syncResponseDispatcher;
	private RemoteEventDispatcher<LedgerStatusUpdate> statusUpdateDispatcher;

	@Before
	public void setUp() {
		this.peersView = mock(PeersView.class);
		this.localSyncService = mock(LocalSyncService.class);
		this.reader = mock(CommittedReader.class);
		this.statusResponseDispatcher =  rmock(RemoteEventDispatcher.class);
		this.syncResponseDispatcher =  rmock(RemoteEventDispatcher.class);
		this.statusUpdateDispatcher =  rmock(RemoteEventDispatcher.class);

		final var initialHeader = mock(LedgerProof.class);
		final var initialAccumulatorState = mock(AccumulatorState.class);
		when(initialHeader.getAccumulatorState()).thenReturn(initialAccumulatorState);
		when(initialAccumulatorState.getStateVersion()).thenReturn(1L);

		this.processor = new RemoteSyncService(
			peersView,
			localSyncService,
			reader,
			statusResponseDispatcher,
			syncResponseDispatcher,
			statusUpdateDispatcher,
			SyncConfig.of(5000L, 10, 5000L, 10, 50),
			mock(SystemCounters.class),
			Comparator.comparingLong(AccumulatorState::getStateVersion),
			initialHeader);
	}

	@Test
	public void when_remote_sync_request__then_process_it() {
		SyncRequest request = mock(SyncRequest.class);
		DtoLedgerProof header = mock(DtoLedgerProof.class);
		when(header.getOpaque()).thenReturn(HashUtils.zero256());
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getHeader()).thenReturn(header);
		BFTNode node = mock(BFTNode.class);
		VerifiedTxnsAndProof verifiedTxnsAndProof = mock(VerifiedTxnsAndProof.class);
		LedgerProof verifiedHeader = mock(LedgerProof.class);
		when(verifiedHeader.toDto()).thenReturn(header);
		when(verifiedTxnsAndProof.getProof()).thenReturn(verifiedHeader);
		when(reader.getNextCommittedTxns(any())).thenReturn(verifiedTxnsAndProof);
		processor.syncRequestEventProcessor().process(node, SyncRequest.create(header));
		verify(syncResponseDispatcher, times(1)).dispatch(eq(node), any());
	}

	@Test
	public void when_remote_sync_request_and_unable__then_dont_do_anything() {
		SyncRequest request = mock(SyncRequest.class);
		DtoLedgerProof header = mock(DtoLedgerProof.class);
		when(header.getOpaque()).thenReturn(HashUtils.zero256());
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getHeader()).thenReturn(header);
		processor.syncRequestEventProcessor().process(BFTNode.random(), SyncRequest.create(header));
		verify(syncResponseDispatcher, never()).dispatch(any(BFTNode.class), any());
	}

	@Test
	public void when_remote_sync_request_and_null_return__then_dont_do_anything() {
		DtoLedgerProof header = mock(DtoLedgerProof.class);
		when(header.getOpaque()).thenReturn(HashUtils.zero256());
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		processor.syncRequestEventProcessor().process(BFTNode.random(), SyncRequest.create(header));
		when(reader.getNextCommittedTxns(any())).thenReturn(null);
		verify(syncResponseDispatcher, never()).dispatch(any(BFTNode.class), any());
	}

	@Test
	public void when_ledger_update_but_syncing__then_dont_send_status_update() {
		final var tail = mock(LedgerProof.class);
		final var ledgerUpdate = mock(LedgerUpdate.class);
		final var accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(2L);
		when(tail.getAccumulatorState()).thenReturn(accumulatorState);
		when(ledgerUpdate.getTail()).thenReturn(tail);

		final var validatorSet = mock(BFTValidatorSet.class);
		when(ledgerUpdate.getNextValidatorSet()).thenReturn(Optional.of(validatorSet));

		when(this.localSyncService.getSyncState())
			.thenReturn(SyncState.SyncingState.init(
				mock(LedgerProof.class),
				ImmutableList.of(),
				mock(LedgerProof.class))
			);

		verifyNoMoreInteractions(peersView);
		verifyNoMoreInteractions(statusUpdateDispatcher);
	}
}
