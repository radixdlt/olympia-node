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
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Objects;
import javax.inject.Inject;

/**
 * Network interface for syncing committed state using the MessageCentral
 */
public final class MessageCentralLedgerSync {
	private final int magic;
	private final MessageCentral messageCentral;
	private final AddressBook addressBook;

	@Inject
	public MessageCentralLedgerSync(
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		this.magic = universe.getMagic();
		this.addressBook = addressBook;
		this.messageCentral = Objects.requireNonNull(messageCentral);
	}

	public Flowable<RemoteEvent<DtoCommandsAndProof>> syncResponses() {
		return this.messageCentral.messagesOf(SyncResponseMessage.class)
			.filter(m -> m.getPeer().hasSystem())
			.map(m -> {
				final var node = BFTNode.create(m.getPeer().getSystem().getKey());
				return RemoteEvent.create(node, m.getMessage().getCommands(), DtoCommandsAndProof.class);
			});
	}

	public Flowable<RemoteEvent<DtoLedgerHeaderAndProof>> syncRequests() {
		return this.messageCentral.messagesOf(SyncRequestMessage.class)
			.filter(m -> m.getPeer().hasSystem())
			.map(m -> {
				final var node = BFTNode.create(m.getPeer().getSystem().getKey());
				return RemoteEvent.create(node, m.getMessage().getCurrentHeader(), DtoLedgerHeaderAndProof.class);
			});
	}

	public RemoteEventDispatcher<DtoLedgerHeaderAndProof> syncRequestDispatcher() {
		return this::sendSyncRequest;
	}

	private void sendSyncRequest(BFTNode node, DtoLedgerHeaderAndProof header) {
		addressBook.peer(node.getKey().euid()).ifPresent(peer -> {
			if (peer.hasSystem()) {
				final SyncRequestMessage syncRequestMessage = new SyncRequestMessage(this.magic, header);
				this.messageCentral.send(peer, syncRequestMessage);
			}
		});
	}

	public RemoteEventDispatcher<DtoCommandsAndProof> syncResponseDispatcher() {
		return this::sendSyncResponse;
	}

	private void sendSyncResponse(BFTNode node, DtoCommandsAndProof commands) {
		addressBook.peer(node.getKey().euid()).ifPresent(peer -> {
			if (peer.hasSystem()) {
				final SyncResponseMessage syncResponseMessage = new SyncResponseMessage(this.magic, commands);
				this.messageCentral.send(peer, syncResponseMessage);
			}
		});
	}
}
