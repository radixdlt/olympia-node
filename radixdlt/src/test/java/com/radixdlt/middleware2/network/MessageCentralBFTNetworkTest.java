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
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vote;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.observers.TestObserver;

import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class MessageCentralBFTNetworkTest {
	private ECPublicKey selfKey;
	private AddressBook addressBook;
	private MessageCentral messageCentral;
	private MessageCentralBFTNetwork network;

	@Before
	public void setUp() {
		this.selfKey = ECKeyPair.generateNew().getPublicKey();
		Universe universe = mock(Universe.class);
		this.addressBook = mock(AddressBook.class);
		this.messageCentral = mock(MessageCentral.class);
		this.network = new MessageCentralBFTNetwork(selfKey, universe, addressBook, messageCentral);
	}

	@Test
	public void when_send_new_view_to_self__then_should_receive_new_view_message() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.consensusEvents().subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.sendNewView(newView, selfKey);
		testObserver.awaitCount(1);
		testObserver.assertValue(newView);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_vote_message() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.consensusEvents().subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.sendVote(vote, selfKey);
		testObserver.awaitCount(1);
		testObserver.assertValue(vote);
	}

	@Test
	public void when_broadcast_proposal__then_should_receive_proposal() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.consensusEvents().subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.broadcastProposal(proposal, Collections.singleton(this.selfKey));
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_send_new_view__then_message_central_should_be_sent_new_view_message() {
		NewView newView = mock(NewView.class);
		ECPublicKey leader = ECKeyPair.generateNew().getPublicKey();
		Peer peer = mock(Peer.class);
		when(peer.getNID()).thenReturn(leader.euid());
		when(addressBook.peer(leader.euid())).thenReturn(Optional.of(peer));

		network.sendNewView(newView, leader);
		verify(messageCentral, times(1)).send(eq(peer), any(ConsensusEventMessage.class));
	}

	@Test
	public void when_send_new_view_to_nonexistent__then_no_message_sent() {
		ECPublicKey otherKey = ECKeyPair.generateNew().getPublicKey();
		NewView newView = mock(NewView.class);
		network.sendNewView(newView, otherKey);
		verify(messageCentral, never()).send(any(), any());
	}

	@Test
	public void when_send_vote__then_message_central_should_be_sent_vote_message() {
		Vote vote = mock(Vote.class);
		ECPublicKey leader = ECKeyPair.generateNew().getPublicKey();
		Peer peer = mock(Peer.class);
		when(peer.getNID()).thenReturn(leader.euid());
		when(addressBook.peer(leader.euid())).thenReturn(Optional.of(peer));

		network.sendVote(vote, leader);
		verify(messageCentral, times(1)).send(eq(peer), any(ConsensusEventMessage.class));
	}
}