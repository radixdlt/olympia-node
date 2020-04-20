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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vote;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class TestEventCoordinatorNetworkTest {
	private ECPublicKey validatorId = ECKeyPair.generateNew().getPublicKey();
	private ECPublicKey validatorId2 = ECKeyPair.generateNew().getPublicKey();

	@Test
	public void when_building_with_negative_latencies__then_illegal_argument_exception_thrown() {
		assertThatThrownBy(() -> TestEventCoordinatorNetwork.builder()
			.minLatency(-1)
			.maxLatency(100)
			.build()).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> TestEventCoordinatorNetwork.builder()
			.minLatency(10)
			.maxLatency(-1)
			.build()).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_builder_with_max_latency__then_created_object_should_have_max_latency() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder()
			.minLatency(50)
			.maxLatency(100)
			.build();

		assertThat(network.getMaxLatency()).isEqualTo(100);
	}

	@Test
	public void when_send_new_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		testObserver.awaitCount(1);
		testObserver.assertValue(newView);
	}

	@Test
	public void when_send_new_view_to_self_twice__then_should_receive_both() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		testObserver.awaitCount(2);
		testObserver.assertValues(newView, newView);
	}

	@Test
	public void when_self_and_other_send_new_view_to_self__then_should_receive_both() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		network.getNetworkSender(validatorId2).sendNewView(newView, validatorId);
		testObserver.awaitCount(2);
		testObserver.assertValues(newView, newView);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
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
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.getNetworkSender(validatorId).broadcastProposal(proposal);
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_disable_and_then_send_new_view_to_self__then_should_not_receive_it() throws Exception {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.setSendingDisable(validatorId, true);
		network.getNetworkRx(validatorId).consensusEvents()
			.subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.getNetworkSender(validatorId).sendNewView(newView, validatorId);
		testObserver.await(10, TimeUnit.MILLISECONDS);
		testObserver.assertEmpty();
	}

	@Test
	public void when_disable_receive_and_other_sends_view_to_self__then_should_receive_it() {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
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
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder().build();
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