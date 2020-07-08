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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.ConsensusRunner.Event;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class ConsensusRunnerTest {
	@Test
	public void when_events_get_emitted__then_event_coordinator_should_be_called() {
		ConsensusEventsRx networkRx = mock(ConsensusEventsRx.class);

		EpochChange epochChange = mock(EpochChange.class);
		EpochChangeRx epochChangeRx = () -> Observable.just(epochChange).concatWith(Observable.never());

		EpochManager epochManager = mock(EpochManager.class);

		LocalTimeout timeout = mock(LocalTimeout.class);
		PacemakerRx pacemakerRx = mock(PacemakerRx.class);
		when(pacemakerRx.localTimeouts()).thenReturn(Observable.just(timeout).concatWith(Observable.never()));

		NewView newView = mock(NewView.class);
		Proposal proposal = mock(Proposal.class);
		Vote vote = mock(Vote.class);

		when(networkRx.consensusEvents())
			.thenReturn(Observable.just(newView, proposal, vote).concatWith(Observable.never()));

		SyncVerticesRPCRx syncVerticesRPCRx = mock(SyncVerticesRPCRx.class);
		GetVerticesRequest request = mock(GetVerticesRequest.class);
		when(syncVerticesRPCRx.requests()).thenReturn(Observable.just(request).concatWith(Observable.never()));
		when(syncVerticesRPCRx.responses()).thenReturn(Observable.never());
		when(syncVerticesRPCRx.errorResponses()).thenReturn(Observable.never());

		SyncEpochsRPCRx syncEpochsRPCRx = mock(SyncEpochsRPCRx.class);
		when(syncEpochsRPCRx.epochRequests()).thenReturn(Observable.never());
		when(syncEpochsRPCRx.epochResponses()).thenReturn(Observable.never());

		VertexStoreEventsRx vertexStoreEventsRx = mock(VertexStoreEventsRx.class);
		Hash id = mock(Hash.class);
		when(vertexStoreEventsRx.syncedVertices()).thenReturn(Observable.just(id).concatWith(Observable.never()));

		CommittedStateSyncRx committedStateSyncRx = mock(CommittedStateSyncRx.class);
		CommittedStateSync stateSync = mock(CommittedStateSync.class);
		when(committedStateSyncRx.committedStateSyncs()).thenReturn(Observable.just(stateSync).concatWith(Observable.never()));

		ConsensusRunner consensusRunner = new ConsensusRunner(
			epochChangeRx,
			networkRx,
			pacemakerRx,
			vertexStoreEventsRx,
			committedStateSyncRx,
			syncVerticesRPCRx,
			syncEpochsRPCRx,
			epochManager
		);

		TestObserver<Event> testObserver = consensusRunner.events().test();
		consensusRunner.start();
		testObserver.awaitCount(8);
		testObserver.assertValueCount(8);
		testObserver.assertNotComplete();

		verify(epochManager, times(1)).processEpochChange(eq(epochChange));
		verify(epochManager, times(1)).processConsensusEvent(eq(vote));
		verify(epochManager, times(1)).processConsensusEvent(eq(proposal));
		verify(epochManager, times(1)).processConsensusEvent(eq(newView));
		verify(epochManager, times(1)).processLocalTimeout(eq(timeout));
		verify(epochManager, times(1)).processLocalSync(eq(id));
		verify(epochManager, times(1)).processCommittedStateSync(eq(stateSync));
		verify(epochManager, times(1)).processGetVerticesRequest(eq(request));

		consensusRunner.shutdown();
	}
}