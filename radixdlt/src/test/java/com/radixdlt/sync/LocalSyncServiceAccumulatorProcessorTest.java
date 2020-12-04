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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

public class LocalSyncServiceAccumulatorProcessorTest {
	private RemoteEventDispatcher<DtoLedgerHeaderAndProof> requestDispatcher;
	private LocalSyncServiceAccumulatorProcessor syncServiceProcessor;
	private ScheduledEventDispatcher<SyncInProgress> syncTimeoutScheduler;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private Comparator<AccumulatorState> accumulatorComparator;
	private AccumulatorState currentAccumulatorState;

	@Before
	public void setUp() {
		this.syncTimeoutScheduler = rmock(ScheduledEventDispatcher.class);
		this.currentHeader = mock(VerifiedLedgerHeaderAndProof.class);
		this.currentAccumulatorState = mock(AccumulatorState.class);
		when(currentHeader.getAccumulatorState()).thenReturn(currentAccumulatorState);
		when(this.currentHeader.toDto()).thenReturn(mock(DtoLedgerHeaderAndProof.class));
		this.accumulatorComparator = rmock(Comparator.class);
		this.requestDispatcher = rmock(RemoteEventDispatcher.class);
		this.syncServiceProcessor = new LocalSyncServiceAccumulatorProcessor(
			requestDispatcher,
			syncTimeoutScheduler,
			accumulatorComparator,
			currentHeader,
			1
		);
	}


	@Test
	public void given_some_current_header__when_local_request_has_equal_target__then_should_do_nothing() {
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		AccumulatorState target = mock(AccumulatorState.class);
		when(targetHeader.getAccumulatorState()).thenReturn(target);
		when(accumulatorComparator.compare(target, currentAccumulatorState)).thenReturn(0);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		syncServiceProcessor.localSyncRequestEventProcessor().process(request);
		verify(requestDispatcher, never()).dispatch(any(BFTNode.class), any());
		verify(syncTimeoutScheduler, never()).dispatch(any(), anyLong());
	}

	@Test
	public void given_some_current_header__when_local_request_has_higher_target__then_should_send_timeout_and_remote_request() {
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		AccumulatorState target = mock(AccumulatorState.class);
		when(targetHeader.getAccumulatorState()).thenReturn(target);
		when(accumulatorComparator.compare(target, currentAccumulatorState)).thenReturn(1);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		when(request.getTargetNodes()).thenReturn(ImmutableList.of(mock(BFTNode.class)));

		syncServiceProcessor.localSyncRequestEventProcessor().process(request);

		verify(requestDispatcher, times(1)).dispatch(any(BFTNode.class), any());
		verify(syncTimeoutScheduler, times(1)).dispatch(any(), anyLong());
	}

	@Test
	public void given_some_sync_in_progress_which_has_been_fulfilled__when_sync_timeout__then_should_do_nothing() {
		AtomicReference<SyncInProgress> sync = new AtomicReference<>();
		doAnswer(invocation -> {
			sync.set(invocation.getArgument(0));
			return null;
		}).when(syncTimeoutScheduler).dispatch(any(), anyLong());
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		AccumulatorState target = mock(AccumulatorState.class);
		when(targetHeader.getAccumulatorState()).thenReturn(target);
		when(accumulatorComparator.compare(target, currentAccumulatorState)).thenReturn(1);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		when(request.getTargetNodes()).thenReturn(ImmutableList.of(mock(BFTNode.class)));
		LedgerUpdate ledgerUpdate = mock(LedgerUpdate.class);

		when(ledgerUpdate.getTail()).thenReturn(targetHeader);
		syncServiceProcessor.localSyncRequestEventProcessor().process(request);
		syncServiceProcessor.processLedgerUpdate(ledgerUpdate);
		syncServiceProcessor.syncTimeoutProcessor().process(sync.get());

		// Once only for initial setup
		verify(requestDispatcher, times(1)).dispatch(any(BFTNode.class), any());
		verify(syncTimeoutScheduler, times(1)).dispatch(any(), anyLong());
	}
}
