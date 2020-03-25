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

import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.consensus.ChainedBFT.Event;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.mempool.Mempool;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class ChainedBFTTest {
	@Test
	public void when_events_get_emitted__then_event_coordinator_should_be_called() {
		EventCoordinatorNetworkRx networkRx = mock(EventCoordinatorNetworkRx.class);

		EpochRx epochRx = () -> Observable.just(mock(ValidatorSet.class)).concatWith(Observable.never());

		PacemakerRx pacemakerRx = mock(PacemakerRx.class);

		NewView newView = mock(NewView.class);
		when(networkRx.newViewMessages())
			.thenReturn(Observable.just(newView).concatWith(Observable.never()));

		Vertex proposal = mock(Vertex.class);
		when(networkRx.proposalMessages())
			.thenReturn(Observable.just(proposal).concatWith(Observable.never()));

		Vote vote = mock(Vote.class);
		when(networkRx.voteMessages())
			.thenReturn(Observable.just(vote).concatWith(Observable.never()));

		View view = mock(View.class);
		when(pacemakerRx.localTimeouts())
			.thenReturn(Observable.just(view).concatWith(Observable.never()));

		ChainedBFT chainedBFT = new ChainedBFT(
			epochRx,
			networkRx,
			pacemakerRx,
			mock(ProposalGenerator.class),
			mock(Mempool.class),
			mock(EventCoordinatorNetworkSender.class),
			mock(SafetyRules.class),
			mock(Pacemaker.class),
			mock(VertexStore.class),
			mock(PendingVotes.class),
			mock(ProposerElection.class),
			mock(ECKeyPair.class)
		);

		TestObserver<Event> testObserver = TestObserver.create();
		chainedBFT.processEvents().subscribe(testObserver);
		testObserver.awaitCount(4);
		testObserver.assertNotComplete();
	}
}