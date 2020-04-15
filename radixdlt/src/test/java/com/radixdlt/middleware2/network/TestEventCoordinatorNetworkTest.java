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

package com.radixdlt.middleware2.network;

import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vote;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class TestEventCoordinatorNetworkTest {
	private static final int TEST_LOOPBACK_LATENCY = 50;
	private ECPublicKey validatorId = ECKeyPair.generateNew().getPublicKey();
	private ECPublicKey validatorId2 = ECKeyPair.generateNew().getPublicKey();

	@Test
	public void when_send_new_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		testObserver.awaitCount(1);
		testObserver.assertValue(newView);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.getNetworkSender(validatorId).sendVote(vote, validatorId);
		testObserver.awaitCount(1);
		testObserver.assertValue(vote);
	}

	@Test
	public void when_broadcast_proposal__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.getNetworkSender(validatorId).broadcastProposal(proposal);
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_disable_and_then_send_new_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.setSendingDisable(validatorId, true);
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		testObserver.assertEmpty();
	}

	@Test
	public void when_disable_receive_and_other_sends_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.setReceivingDisable(validatorId, true);
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId2).sendNewView(newView, validatorId);
		testObserver.assertEmpty();
	}

	@Test
	public void when_disable_then_reenable_receive_and_other_sends_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.orderedLatent(TEST_LOOPBACK_LATENCY);
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.setReceivingDisable(validatorId, true);
		network.setReceivingDisable(validatorId, false);
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId2).sendNewView(newView, validatorId);
		testObserver.awaitCount(1);
		testObserver.assertValue(newView);
	}
}