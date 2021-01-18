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

package com.radixdlt.middleware2.network;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.core.Flowable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;

/**
 * Network layer for the mempool
 */
public final class MessageCentralMempool {
	private static final Logger log = LogManager.getLogger();

	private final MessageCentral messageCentral;
	private final int magic;
	private final AddressBook addressBook;

	@Inject
	public MessageCentralMempool(
		Universe universe,
		MessageCentral messageCentral,
		AddressBook addressBook
	) {
		this.magic = universe.getMagic();
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.addressBook = addressBook;
	}

	public RemoteEventDispatcher<MempoolAddSuccess> commandRemoteEventDispatcher() {
		return (receiver, msg) -> {
			MempoolAtomAddedMessage message = new MempoolAtomAddedMessage(this.magic, msg.getCommand());
			this.send(message, receiver);
		};
	}

	private boolean send(Message message, BFTNode recipient) {
		Optional<PeerWithSystem> peer = this.addressBook.peer(recipient.getKey().euid());

		if (!peer.isPresent()) {
			log.error("Peer {} not present", recipient);
			return false;
		} else {
			this.messageCentral.send(peer.get(), message);
			return true;
		}
	}

	public Flowable<RemoteEvent<MempoolAddSuccess>> mempoolComands() {
		return messageCentral.messagesOf(MempoolAtomAddedMessage.class)
			.map(msg -> {
				final BFTNode node = BFTNode.create(msg.getPeer().getSystem().getKey());
				return RemoteEvent.create(
					node,
					MempoolAddSuccess.create(msg.getMessage().command()),
					MempoolAddSuccess.class
				);
			});
	}
}
