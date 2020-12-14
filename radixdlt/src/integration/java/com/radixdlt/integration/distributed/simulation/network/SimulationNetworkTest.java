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

package com.radixdlt.integration.distributed.simulation.network;

import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.ChannelCommunication;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;

public class SimulationNetworkTest {
	private BFTNode node1;
	private BFTNode node2;
	private ChannelCommunication channelCommunication;
	private SimulationNetwork network;

	@Before
	public void setup() {
		node1 = mock(BFTNode.class);
		node2 = mock(BFTNode.class);
		this.channelCommunication = new InOrderChannels(msg -> 50);
		this.network = new SimulationNetwork(channelCommunication);
	}

	@Test
	public void when_send_vote_to_self_twice__then_should_receive_both() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetwork(node1).bftEvents()
			.subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.getNetwork(node1).remoteEventDispatcher(Vote.class).dispatch(node1, vote);
		network.getNetwork(node1).remoteEventDispatcher(Vote.class).dispatch(node1, vote);
		testObserver.awaitCount(2);
		testObserver.assertValues(vote, vote);
	}

	@Test
	public void when_self_and_other_send_vote_to_self__then_should_receive_both() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetwork(node1).bftEvents()
			.subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.getNetwork(node1).remoteEventDispatcher(Vote.class).dispatch(node1, vote);
		network.getNetwork(node1).remoteEventDispatcher(Vote.class).dispatch(node1, vote);
		testObserver.awaitCount(2);
		testObserver.assertValues(vote, vote);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_it() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetwork(node1).bftEvents()
			.subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.getNetwork(node1).remoteEventDispatcher(Vote.class).dispatch(node1, vote);
		testObserver.awaitCount(1);
		testObserver.assertValue(vote);
	}

	@Test
	public void when_broadcast_proposal__then_should_receive_it() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetwork(node1).bftEvents()
			.subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.getNetwork(node1).broadcastProposal(proposal, ImmutableSet.of(node1, node2));
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_disabling_messages_and_send_vote_message_to_other_node__then_should_not_receive_it() {
		SimulationNetwork network = new SimulationNetwork(new InOrderChannels(msg -> -1));

		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.getNetwork(node2).bftEvents()
			.subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.getNetwork(node1).remoteEventDispatcher(Vote.class).dispatch(node1, vote);
		testObserver.awaitCount(1);
		testObserver.assertEmpty();
	}

	@Test
	public void when_send_get_vertex_request_to_another_node__then_should_receive_it() {
		HashCode vertexId = mock(HashCode.class);

		TestObserver<RemoteEvent<GetVerticesRequest>> rpcRequestListener =
			network.getNetwork(node2).remoteEvents(GetVerticesRequest.class).test();

		network
			.getNetwork(node1)
			.remoteEventDispatcher(GetVerticesRequest.class)
			.dispatch(node2, new GetVerticesRequest(vertexId, 1));

		rpcRequestListener.awaitCount(1);
		rpcRequestListener.assertValueAt(0, r -> r.getEvent().getVertexId().equals(vertexId));
	}

}
