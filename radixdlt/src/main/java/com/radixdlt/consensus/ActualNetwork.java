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

package com.radixdlt.consensus;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;

import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessageCentral;

import com.radixdlt.common.EUID;
import com.radixdlt.consensus.messages.NewRoundMessage;
import com.radixdlt.consensus.messages.VertexMessage;
import com.radixdlt.consensus.messages.VoteMessage;
import com.radixdlt.universe.Universe;

/**
 * Overly simplistic network implementation that just sends messages to itself.
 */
public class ActualNetwork implements NetworkSender, NetworkRx {
	public static final int LOOPBACK_DELAY = 100;

	private final EUID self;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;

	private final PublishSubject<Vertex> proposals;
	private final PublishSubject<NewRound> newRounds;
	private final PublishSubject<Vote> votes;

	@Inject
	public ActualNetwork(
		@Named("self") EUID self,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		this.self = Objects.requireNonNull(self);
		this.magic = universe.getMagic();
		this.addressBook = Objects.requireNonNull(addressBook);
		this.messageCentral = Objects.requireNonNull(messageCentral);

		this.proposals = PublishSubject.create();
		this.newRounds = PublishSubject.create();
		this.votes = PublishSubject.create();

		// FIXME: Should be handled in start()/stop() once we have lifetimes sorted out
		this.messageCentral.addListener(VertexMessage.class, this::handleVertexMessage);
		this.messageCentral.addListener(NewRoundMessage.class, this::handleNewRoundMessage);
		this.messageCentral.addListener(VoteMessage.class, this::handleVoteMessage);
	}

	@Override
	public void broadcastProposal(Vertex vertex) {
		sendToAll(new VertexMessage(this.magic, vertex));
	}

	@Override
	public void sendNewRound(NewRound newRound) {
		sendToAll(new NewRoundMessage(this.magic, newRound));
	}

	@Override
	public void sendVote(Vote vote) {
		sendToAll(new VoteMessage(this.magic, vote));
	}

	@Override
	public Observable<Vertex> proposalMessages() {
		return proposals;
	}

	@Override
	public Observable<NewRound> newRoundMessages() {
		return newRounds;
	}

	@Override
	public Observable<Vote> voteMessages() {
		return votes;
	}

	private void sendToAll(Message message) {
		this.addressBook.peers()
			.forEach(peer -> this.messageCentral.send(peer, message));
	}

	private void handleVertexMessage(Peer source, VertexMessage message) {
		this.proposals.onNext(message.vertex());
	}

	private void handleNewRoundMessage(Peer source, NewRoundMessage message) {
		this.newRounds.onNext(message.newRound());
	}

	private void handleVoteMessage(Peer source, VoteMessage message) {
		this.votes.onNext(message.vote());
	}
}
