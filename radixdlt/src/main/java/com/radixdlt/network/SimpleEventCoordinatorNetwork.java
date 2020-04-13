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

import com.radixdlt.consensus.ConsensusMessage;
import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.EventCoordinatorNetworkSender;
import com.radixdlt.consensus.Proposal;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Objects;
import javax.inject.Inject;
import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.addressbook.PeerWithSystem;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.messaging.MessageListener;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vote;
import com.radixdlt.universe.Universe;

/**
 * Simple network that publishes messages to known nodes.
 */
public class SimpleEventCoordinatorNetwork implements EventCoordinatorNetworkSender, EventCoordinatorNetworkRx {
	private final PeerWithSystem localPeer;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;
	private final PublishSubject<ConsensusMessage> localMessages;

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

		this.localMessages = PublishSubject.create();
	}

	@Override
	public Observable<ConsensusMessage> consensusMessages() {
		return Observable.<ConsensusMessage>create(emitter -> {
			MessageListener<ConsensusMessageDto> listener =
				(src, msg) -> emitter.onNext(msg.getConsensusMessage());
			this.messageCentral.addListener(ConsensusMessageDto.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		}).mergeWith(localMessages);
	}

	@Override
	public void broadcastProposal(Proposal proposal) {
		this.localMessages.onNext(proposal);
		ConsensusMessageDto message = new ConsensusMessageDto(this.magic, proposal);
		broadcast(message);
	}

	@Override
	public void sendNewView(NewView newView, EUID newViewLeader) {
		if (this.localPeer.getNID().equals(newViewLeader)) {
			this.localMessages.onNext(newView);
		} else {
			ConsensusMessageDto message = new ConsensusMessageDto(this.magic, newView);
			send(message, newViewLeader);
		}
	}

	@Override
	public void sendVote(Vote vote, EUID leader) {
		if (this.localPeer.getNID().equals(leader)) {
			this.localMessages.onNext(vote);
		} else {
			ConsensusMessageDto message = new ConsensusMessageDto(this.magic, vote);
			send(message, leader);
		}
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
}
