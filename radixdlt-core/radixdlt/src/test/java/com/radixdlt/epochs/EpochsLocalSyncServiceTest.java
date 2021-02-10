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

package com.radixdlt.epochs;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncService;

import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

public class EpochsLocalSyncServiceTest {
	private EpochsLocalSyncService processor;
	private LocalSyncService initialProcessor;
	private EpochChange initialEpoch;
	private LocalSyncServiceFactory localSyncFactory;

	private EventProcessor<LocalSyncRequest> localSyncEventProcessor;
	private EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor;

	@Before
	public void setup() {
		this.initialProcessor = mock(LocalSyncService.class);
		this.localSyncEventProcessor = rmock(EventProcessor.class);
		this.ledgerUpdateEventProcessor = rmock(EventProcessor.class);
		when(initialProcessor.localSyncRequestEventProcessor()).thenReturn(localSyncEventProcessor);
		when(initialProcessor.ledgerUpdateEventProcessor()).thenReturn(ledgerUpdateEventProcessor);
		this.initialEpoch = mock(EpochChange.class);
		this.localSyncFactory = mock(LocalSyncServiceFactory.class);
		this.processor = new EpochsLocalSyncService(
			this.initialProcessor,
			this.initialEpoch,
			this.localSyncFactory
		);
	}

    @Test
    public void when_local_sync_request_for_previous_epoch__then_should_do_nothing() {
		when(initialEpoch.getEpoch()).thenReturn(2L);

		VerifiedLedgerHeaderAndProof header = mock(VerifiedLedgerHeaderAndProof.class);
		when(header.getEpoch()).thenReturn(1L);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(header);

		processor.localSyncRequestEventProcessor().process(request);

		verify(localSyncEventProcessor, never()).process(any());
    }

	@Test
	public void when_local_sync_request_for_current_epoch__then_should_forward() {
		when(initialEpoch.getEpoch()).thenReturn(2L);

		VerifiedLedgerHeaderAndProof header = mock(VerifiedLedgerHeaderAndProof.class);
		when(header.getEpoch()).thenReturn(2L);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(header);

		processor.localSyncRequestEventProcessor().process(request);

		verify(localSyncEventProcessor, times(1)).process(request);
	}

	@Test
	public void when_ledger_update_without_epoch_change__then_should_forward() {
		EpochsLedgerUpdate ledgerUpdate = mock(EpochsLedgerUpdate.class);
		when(ledgerUpdate.getEpochChange()).thenReturn(Optional.empty());

		processor.epochsLedgerUpdateEventProcessor().process(ledgerUpdate);

		verify(ledgerUpdateEventProcessor, times(1)).process(ledgerUpdate);
	}

	@Test
	public void when_ledger_update_with_epoch_change__then_should_create_and_use_new_local_sync() {
		EpochsLedgerUpdate ledgerUpdate = mock(EpochsLedgerUpdate.class);
		BFTConfiguration bftConfig = mock(BFTConfiguration.class);
		when(bftConfig.getValidatorSet()).thenReturn(mock(BFTValidatorSet.class));
		EpochChange epochChange = mock(EpochChange.class);
		when(epochChange.getBFTConfiguration()).thenReturn(bftConfig);
		when(ledgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));

		EventProcessor<LedgerUpdate> newLedgerUpdateProcessor = rmock(EventProcessor.class);
		LocalSyncService newLocalSyncService = mock(LocalSyncService.class);
		when(newLocalSyncService.ledgerUpdateEventProcessor()).thenReturn(newLedgerUpdateProcessor);
		when(localSyncFactory.create(any(), any())).thenReturn(newLocalSyncService);

		processor.epochsLedgerUpdateEventProcessor().process(ledgerUpdate);

		verify(ledgerUpdateEventProcessor, times(0)).process(ledgerUpdate);
		verify(newLedgerUpdateProcessor, times(1)).process(ledgerUpdate);
	}
}
