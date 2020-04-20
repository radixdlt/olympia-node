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

import java.util.Objects;

import java.util.Optional;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import com.google.inject.name.Named;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.EventCoordinatorNetworkSender;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * Simple network that publishes messages to known nodes.
 */
public class SimpleEventCoordinatorNetwork implements EventCoordinatorNetworkSender, EventCoordinatorNetworkRx {
	private static final Logger log = LogManager.getLogger();

	private final ECPublicKey selfPublicKey;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;
	private final PublishSubject<ConsensusEvent> localMessages;

	@Inject
	public SimpleEventCoordinatorNetwork(
		@Named("self") ECPublicKey selfPublicKey,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		this.magic = universe.getMagic();
		this.selfPublicKey = Objects.requireNonNull(selfPublicKey);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.localMessages = PublishSubject.create();
	}

	@Override
	public Observable<ConsensusEvent> consensusEvents() {
		return Observable.<ConsensusEvent>create(emitter -> {
			MessageListener<ConsensusEventMessage> listener =
				(src, msg) -> emitter.onNext(msg.getConsensusMessage());
			this.messageCentral.addListener(ConsensusEventMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		}).mergeWith(localMessages);
	}

	@Override
	public void broadcastProposal(Proposal proposal) {
		this.localMessages.onNext(proposal);
		ConsensusEventMessage message = new ConsensusEventMessage(this.magic, proposal);
		broadcast(message);
	}

	@Override
	public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
		if (this.selfPublicKey.equals(newViewLeader)) {
			this.localMessages.onNext(newView);
		} else {
			ConsensusEventMessage message = new ConsensusEventMessage(this.magic, newView);
			send(message, newViewLeader);
		}
	}

	@Override
	public void sendVote(Vote vote, ECPublicKey leader) {
		if (this.selfPublicKey.equals(leader)) {
			this.localMessages.onNext(vote);
		} else {
			ConsensusEventMessage message = new ConsensusEventMessage(this.magic, vote);
			send(message, leader);
		}
	}

	private void send(Message message, ECPublicKey recipient) {
		Optional<Peer> peer = this.addressBook.peer(recipient.euid());

		if (!peer.isPresent()) {
			log.error("Peer with pubkey {} not present", recipient);
		} else {
			this.messageCentral.send(peer.get(), message);
		}
	}

	// TODO: use a validator set to ensure every validator gets message
	private void broadcast(Message message) {
		this.addressBook.peers()
			.filter(Peer::hasSystem) // Only peers with systems (and therefore transports)
			.filter(p -> !selfPublicKey.euid().equals(p.getNID())) // Exclude self, already sent
			.forEach(peer -> this.messageCentral.send(peer, message));
	}
}
