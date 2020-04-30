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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.ConsensusRunner.Event;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.validators.ValidatorSet;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class ConsensusRunnerTest {
	@Test
	public void when_events_get_emitted__then_event_coordinator_should_be_called() {
		EventCoordinatorNetworkRx networkRx = mock(EventCoordinatorNetworkRx.class);

		EpochRx epochRx = () -> Observable.just(mock(ValidatorSet.class)).concatWith(Observable.never());

		BFTEventProcessor ec = mock(BFTEventProcessor.class);
		EpochManager epochManager = mock(EpochManager.class);
		when(epochManager.start()).thenReturn(ec);
		when(epochManager.nextEpoch(any())).thenReturn(ec);

		View timeout = mock(View.class);
		PacemakerRx pacemakerRx = mock(PacemakerRx.class);
		when(pacemakerRx.localTimeouts()).thenReturn(Observable.just(timeout).concatWith(Observable.never()));

		NewView newView = mock(NewView.class);
		Proposal proposal = mock(Proposal.class);
		Vote vote = mock(Vote.class);

		when(networkRx.consensusEvents())
			.thenReturn(Observable.just(newView, proposal, vote).concatWith(Observable.never()));

		GetVertexRequest request = mock(GetVertexRequest.class);
		when(networkRx.rpcRequests())
			.thenReturn(Observable.just(request).concatWith(Observable.never()));

		ConsensusRunner consensusRunner = new ConsensusRunner(
			epochRx,
			networkRx,
			pacemakerRx,
			epochManager
		);

		TestObserver<Event> testObserver = TestObserver.create();
		consensusRunner.events().subscribe(testObserver);
		consensusRunner.start();
		testObserver.awaitCount(6);
		testObserver.assertValueCount(6);
		testObserver.assertNotComplete();

		verify(ec, times(1)).processVote(eq(vote));
		verify(ec, times(1)).processProposal(eq(proposal));
		verify(ec, times(1)).processNewView(eq(newView));
		verify(ec, times(1)).processLocalTimeout(eq(timeout));
		verify(ec, times(1)).processGetVertexRequest(eq(request));
	}
}