/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.EpochScheduledLocalTimeout;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.junit.Test;

public class EpochManagerRunnerTest {
	@Test
	public void when_events_get_emitted__then_event_coordinator_should_be_called() {
		BFTEventsRx networkRx = mock(BFTEventsRx.class);

		Subject<EpochsLedgerUpdate> ledgerUpdates = PublishSubject.create();
		Subject<BFTUpdate> bftUpdates = PublishSubject.create();
		Subject<LocalGetVerticesRequest> syncTimeouts = PublishSubject.create();
		Subject<EpochViewUpdate> localViewUpdates = PublishSubject.create();

		EpochManager epochManager = mock(EpochManager.class);

		EpochScheduledLocalTimeout timeout = mock(EpochScheduledLocalTimeout.class);
		PacemakerRx pacemakerRx = mock(PacemakerRx.class);
		when(pacemakerRx.localTimeouts()).thenReturn(Observable.just(timeout).concatWith(Observable.never()));

		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		Proposal proposal = mock(Proposal.class);
		Vote vote = mock(Vote.class);

		when(networkRx.bftEvents())
			.thenReturn(Observable.just(viewTimeout, proposal, vote).concatWith(Observable.never()));

		SyncVerticesRPCRx syncVerticesRPCRx = mock(SyncVerticesRPCRx.class);
		GetVerticesRequest request = mock(GetVerticesRequest.class);
		when(syncVerticesRPCRx.requests()).thenReturn(Observable.just(request).concatWith(Observable.never()));
		when(syncVerticesRPCRx.responses()).thenReturn(Observable.never());
		when(syncVerticesRPCRx.errorResponses()).thenReturn(Observable.never());

		SyncEpochsRPCRx syncEpochsRPCRx = mock(SyncEpochsRPCRx.class);
		when(syncEpochsRPCRx.epochRequests()).thenReturn(Observable.never());
		when(syncEpochsRPCRx.epochResponses()).thenReturn(Observable.never());

		EpochManagerRunner consensusRunner = new EpochManagerRunner(
			ledgerUpdates,
			bftUpdates,
			epochManager::processBFTUpdate,
			syncTimeouts,
			epochManager::processGetVerticesLocalTimeout,
			localViewUpdates,
			rmock(EventProcessor.class),
			networkRx,
			pacemakerRx,
			syncVerticesRPCRx,
			syncEpochsRPCRx,
			epochManager
		);

		consensusRunner.start();

		EpochsLedgerUpdate epochsLedgerUpdate = mock(EpochsLedgerUpdate.class);
		ledgerUpdates.onNext(epochsLedgerUpdate);
		BFTUpdate bftUpdate = mock(BFTUpdate.class);
		bftUpdates.onNext(bftUpdate);

		verify(epochManager, timeout(1000).times(1)).processLedgerUpdate(eq(epochsLedgerUpdate));
		verify(epochManager, timeout(1000).times(1)).processConsensusEvent(eq(vote));
		verify(epochManager, timeout(1000).times(1)).processConsensusEvent(eq(proposal));
		verify(epochManager, timeout(1000).times(1)).processConsensusEvent(eq(viewTimeout));
		verify(epochManager, timeout(1000).times(1)).processLocalTimeout(eq(timeout));
		verify(epochManager, timeout(1000).times(1)).processBFTUpdate(eq(bftUpdate));
		verify(epochManager, timeout(1000).times(1)).processGetVerticesRequest(eq(request));

		consensusRunner.shutdown();
	}
}
