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

package com.radixdlt.epochs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor;
import com.radixdlt.utils.TypedMocks;

import java.util.Optional;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

public class EpochsLocalSyncServiceProcessorTest {
	private EpochsLocalSyncServiceProcessor processor;
	private LocalSyncServiceAccumulatorProcessor initialProcessor;
	private EpochChange initialEpoch;
	private VerifiedLedgerHeaderAndProof initialHeader;
	private Function<BFTConfiguration, LocalSyncServiceAccumulatorProcessor> localSyncFactory;
	private SyncedEpochSender syncedEpochSender;

	private EventProcessor<LocalSyncRequest> eventProcessor;
	private RemoteEventDispatcher<DtoLedgerHeaderAndProof> requestDispatcher;

	@Before
	public void setup() {
		this.initialProcessor = mock(LocalSyncServiceAccumulatorProcessor.class);
		this.eventProcessor = TypedMocks.rmock(EventProcessor.class);
		when(initialProcessor.localSyncRequestEventProcessor()).thenReturn(eventProcessor);
		this.requestDispatcher = TypedMocks.rmock(RemoteEventDispatcher.class);
		this.initialEpoch = mock(EpochChange.class);
		this.initialHeader = mock(VerifiedLedgerHeaderAndProof.class);
		this.localSyncFactory = TypedMocks.rmock(Function.class);
		this.syncedEpochSender = mock(SyncedEpochSender.class);
		this.processor = new EpochsLocalSyncServiceProcessor(
			this.initialProcessor,
			this.initialEpoch,
			this.initialHeader,
			this.localSyncFactory,
			this.requestDispatcher,
			this.syncedEpochSender
		);
	}

	@Test
	public void given_current_epoch_1__and_request_for_epoch_1_with_different_accumulator__then_should_forward_to_processor() {
		when(initialEpoch.getEpoch()).thenReturn(1L);

		LocalSyncRequest request = mock(LocalSyncRequest.class);
		VerifiedLedgerHeaderAndProof header = mock(VerifiedLedgerHeaderAndProof.class);
		when(header.getAccumulatorState()).thenReturn(mock(AccumulatorState.class));
		when(header.getEpoch()).thenReturn(1L);
		when(request.getTarget()).thenReturn(header);
		processor.localSyncRequestEventProcessor().process(request);

		verify(requestDispatcher, never()).dispatch(any(), any());
		verify(syncedEpochSender, never()).sendSyncedEpoch(any());
		verify(eventProcessor, times(1)).process(eq(request));
	}

	@Test
	public void given_current_epoch_1__and_request_for_epoch_2__then_should_send_epoch_sync_request() {
		when(initialEpoch.getEpoch()).thenReturn(1L);
		when(initialEpoch.getProof()).thenReturn(mock(VerifiedLedgerHeaderAndProof.class));

		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTargetNodes()).thenReturn(ImmutableList.of(mock(BFTNode.class)));
		VerifiedLedgerHeaderAndProof header = mock(VerifiedLedgerHeaderAndProof.class);
		when(header.getEpoch()).thenReturn(2L);
		when(request.getTarget()).thenReturn(header);
		processor.localSyncRequestEventProcessor().process(request);

		verify(requestDispatcher, times(1)).dispatch(any(), any());
		verify(syncedEpochSender, never()).sendSyncedEpoch(any());
		verify(eventProcessor, never()).process(any());
	}

	@Test
	public void given_current_epoch_1__and_ledger_epoch_update_and_epoch_2_request_with_equal_accumulator__then_should_do_nothing() {
		when(initialEpoch.getEpoch()).thenReturn(1L);
		when(initialEpoch.getProof()).thenReturn(mock(VerifiedLedgerHeaderAndProof.class));
		LocalSyncServiceAccumulatorProcessor localSyncProcessor = mock(LocalSyncServiceAccumulatorProcessor.class);
		when(localSyncFactory.apply(any())).thenReturn(localSyncProcessor);
		EventProcessor<LocalSyncRequest> localSyncEventProcessor = TypedMocks.rmock(EventProcessor.class);
		when(localSyncProcessor.localSyncRequestEventProcessor()).thenReturn(localSyncEventProcessor);

		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		EpochsLedgerUpdate ledgerUpdate = mock(EpochsLedgerUpdate.class);
		EpochChange epochChange = mock(EpochChange.class);
		when(epochChange.getEpoch()).thenReturn(2L);
		BFTConfiguration configuration = mock(BFTConfiguration.class);
		VerifiedLedgerHeaderAndProof genesisHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(genesisHeader.getAccumulatorState()).thenReturn(accumulatorState);
		when(genesisHeader.isEndOfEpoch()).thenReturn(false);
		when(configuration.getRootHeader()).thenReturn(genesisHeader);
		when(epochChange.getBFTConfiguration()).thenReturn(configuration);
		when(ledgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));
		processor.epochsLedgerUpdateEventProcessor().process(ledgerUpdate);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		VerifiedLedgerHeaderAndProof header = mock(VerifiedLedgerHeaderAndProof.class);
		when(header.getAccumulatorState()).thenReturn(accumulatorState);
		when(header.getEpoch()).thenReturn(2L);
		when(header.isEndOfEpoch()).thenReturn(false);
		when(request.getTarget()).thenReturn(header);
		processor.localSyncRequestEventProcessor().process(request);

		verify(requestDispatcher, never()).dispatch(any(), any());
		verify(syncedEpochSender, never()).sendSyncedEpoch(any());
		verify(eventProcessor, never()).process(any());
		verify(localSyncEventProcessor, never()).process(any());
	}

	@Test
	public void given_current_epoch_1__and_ledger_update_and_epoch_1_request_with_different_accumulator__then_should_forward() {
		when(initialEpoch.getEpoch()).thenReturn(1L);
		when(initialEpoch.getProof()).thenReturn(mock(VerifiedLedgerHeaderAndProof.class));
		LocalSyncServiceAccumulatorProcessor localSyncProcessor = mock(LocalSyncServiceAccumulatorProcessor.class);
		when(localSyncFactory.apply(any())).thenReturn(localSyncProcessor);
		EventProcessor<LocalSyncRequest> localSyncEventProcessor = TypedMocks.rmock(EventProcessor.class);
		when(localSyncProcessor.localSyncRequestEventProcessor()).thenReturn(localSyncEventProcessor);

		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		EpochsLedgerUpdate ledgerUpdate = mock(EpochsLedgerUpdate.class);
		VerifiedLedgerHeaderAndProof tail = mock(VerifiedLedgerHeaderAndProof.class);
		when(tail.getAccumulatorState()).thenReturn(accumulatorState);
		when(ledgerUpdate.getTail()).thenReturn(tail);
		when(ledgerUpdate.getEpochChange()).thenReturn(Optional.empty());
		processor.epochsLedgerUpdateEventProcessor().process(ledgerUpdate);

		LocalSyncRequest request = mock(LocalSyncRequest.class);
		VerifiedLedgerHeaderAndProof header = mock(VerifiedLedgerHeaderAndProof.class);
		when(header.getAccumulatorState()).thenReturn(mock(AccumulatorState.class));
		when(header.getEpoch()).thenReturn(1L);
		when(request.getTarget()).thenReturn(header);
		processor.localSyncRequestEventProcessor().process(request);

		verify(requestDispatcher, never()).dispatch(any(), any());
		verify(syncedEpochSender, never()).sendSyncedEpoch(any());
		verify(eventProcessor, times(1)).process(any());
		verify(localSyncEventProcessor, never()).process(any());
	}
}