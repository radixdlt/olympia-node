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

package com.radixdlt.network;

import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.EventCoordinatorNetworkSender;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Objects;
import javax.inject.Inject;
import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.addressbook.PeerWithSystem;
import org.radix.network2.messaging.MessageCentral;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.common.EUID;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.messages.NewViewMessage;
import com.radixdlt.consensus.messages.VertexMessage;
import com.radixdlt.consensus.messages.VoteMessage;
import com.radixdlt.universe.Universe;

/**
 * Simple network that publishes messages to known nodes.
 */
public class SimpleEventCoordinatorNetwork implements EventCoordinatorNetworkSender, EventCoordinatorNetworkRx {
	private final PeerWithSystem localPeer;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;

	private final PublishSubject<Vertex> proposals;
	private final PublishSubject<NewView> newViews;
	private final PublishSubject<Vote> votes;

	@Inject
	public SimpleEventCoordinatorNetwork(
		LocalSystem system,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		this.magic = universe.getMagic();
		this.addressBook = Objects.requireNonNull(addressBook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.localPeer = new PeerWithSystem(system);

		this.proposals = PublishSubject.create();
		this.newViews = PublishSubject.create();
		this.votes = PublishSubject.create();

		// TODO: Should be handled in start()/stop() once we have lifetimes sorted out
		this.messageCentral.addListener(VertexMessage.class, this::handleVertexMessage);
		this.messageCentral.addListener(NewViewMessage.class, this::handleNewViewMessage);
		this.messageCentral.addListener(VoteMessage.class, this::handleVoteMessage);
	}

	@Override
	public void broadcastProposal(Vertex vertex) {
		VertexMessage message = new VertexMessage(this.magic, vertex);
		handleVertexMessage(this.localPeer, message);
		broadcast(message);
	}

	@Override
	public void sendNewView(NewView newView, EUID newViewLeader) {
		NewViewMessage message = new NewViewMessage(this.magic, newView);
		if (this.localPeer.getNID().equals(newViewLeader)) {
			handleNewViewMessage(this.localPeer, message);
		} else {
			send(message, newViewLeader);
		}
	}

	@Override
	public void sendVote(Vote vote, EUID leader) {
		VoteMessage message = new VoteMessage(this.magic, vote);
		if (this.localPeer.getNID().equals(leader)) {
			handleVoteMessage(this.localPeer, message);
		} else {
			send(message, leader);
		}
	}

	@Override
	public Observable<Vertex> proposalMessages() {
		return this.proposals;
	}

	@Override
	public Observable<NewView> newViewMessages() {
		return this.newViews;
	}

	@Override
	public Observable<Vote> voteMessages() {
		return this.votes;
	}

	private void send(Message message, EUID recipient) {
		this.addressBook.peers()
			.filter(p -> p.getNID().equals(recipient))
			.forEach(p -> this.messageCentral.send(p, message));
	}

	private void broadcast(Message message) {
		final EUID self = this.localPeer.getNID();
		this.addressBook.peers()
			.filter(Peer::hasSystem) // Only peers with systems (and therefore transports)
			.filter(p -> !self.equals(p.getNID())) // Exclude self, already sent
			.forEach(peer -> this.messageCentral.send(peer, message));
	}

	private void handleVertexMessage(Peer source, VertexMessage message) {
		this.proposals.onNext(message.vertex());
	}

	private void handleNewViewMessage(Peer source, NewViewMessage message) {
		this.newViews.onNext(message.newView());
	}

	private void handleVoteMessage(Peer source, VoteMessage message) {
		this.votes.onNext(message.vote());
	}
}
