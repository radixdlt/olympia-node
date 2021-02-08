/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.consensus.Vote;
import com.radixdlt.network.messaging.MessageCentralMockProvider;
import com.radixdlt.universe.Universe;

import java.util.Collections;
import java.util.Optional;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;

public class MessageCentralBFTNetworkTest {
	private BFTNode self;
	private AddressBook addressBook;
	private MessageCentral messageCentral;
	private MessageCentralBFTNetwork network;

	@Before
	public void setUp() {
		this.self = mock(BFTNode.class);
		Universe universe = mock(Universe.class);
		this.addressBook = mock(AddressBook.class);
		this.messageCentral = MessageCentralMockProvider.get();
		this.network = new MessageCentralBFTNetwork(self, universe, addressBook, messageCentral);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_vote_message() {
		TestSubscriber<ConsensusEvent> testObserver = TestSubscriber.create();
		network.localBftEvents().subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.voteDispatcher().dispatch(self, vote);
		testObserver.awaitCount(1);
		testObserver.assertValue(vote);
	}

	@Test
	public void when_broadcast_proposal__then_should_receive_proposal() {
		TestSubscriber<ConsensusEvent> testObserver = TestSubscriber.create();
		network.localBftEvents().subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.broadcastProposal(proposal, Collections.singleton(this.self));
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_send_vote_to_nonexistent__then_no_message_sent() {
		Vote vote = mock(Vote.class);
		BFTNode node = mock(BFTNode.class);
		when(node.getKey()).thenReturn(mock(ECPublicKey.class));
		network.voteDispatcher().dispatch(node, vote);
		verify(messageCentral, never()).send(any(), any());
	}

	@Test
	public void when_send_vote__then_message_central_should_be_sent_vote_message() {
		Vote vote = mock(Vote.class);
		ECPublicKey leaderPk = ECKeyPair.generateNew().getPublicKey();
		BFTNode leader = mock(BFTNode.class);
		when(leader.getKey()).thenReturn(leaderPk);
		PeerWithSystem peer = mock(PeerWithSystem.class);
		when(peer.getNID()).thenReturn(leaderPk.euid());
		when(addressBook.peer(leaderPk.euid())).thenReturn(Optional.of(peer));

		network.voteDispatcher().dispatch(leader, vote);
		verify(messageCentral, times(1)).send(eq(peer), any(ConsensusEventMessage.class));
	}
}
