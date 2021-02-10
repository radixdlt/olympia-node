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
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
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

	public Flowable<RemoteEvent<StatusRequest>> statusRequests() {
		return this.messageCentral.messagesOf(StatusRequestMessage.class)
			.filter(m -> m.getPeer().hasSystem())
			.map(m -> {
				final var node = BFTNode.create(m.getPeer().getSystem().getKey());
				return RemoteEvent.create(node, StatusRequest.create(), StatusRequest.class);
			});
	}

	public Flowable<RemoteEvent<StatusResponse>> statusResponses() {
		return this.messageCentral.messagesOf(StatusResponseMessage.class)
			.filter(m -> m.getPeer().hasSystem())
			.map(m -> {
				final var node = BFTNode.create(m.getPeer().getSystem().getKey());
				final var msg = m.getMessage();
				return RemoteEvent.create(node, StatusResponse.create(msg.getHeader()), StatusResponse.class);
			});
	}

	public Flowable<RemoteEvent<SyncRequest>> syncRequests() {
		return this.messageCentral.messagesOf(SyncRequestMessage.class)
			.filter(m -> m.getPeer().hasSystem())
			.map(m -> {
				final var node = BFTNode.create(m.getPeer().getSystem().getKey());
				final var msg = m.getMessage();
				return RemoteEvent.create(node, SyncRequest.create(msg.getCurrentHeader()), SyncRequest.class);
			});
	}

	public Flowable<RemoteEvent<SyncResponse>> syncResponses() {
		return this.messageCentral.messagesOf(SyncResponseMessage.class)
			.filter(m -> m.getPeer().hasSystem())
			.map(m -> {
				final var node = BFTNode.create(m.getPeer().getSystem().getKey());
				final var msg = m.getMessage();
				return RemoteEvent.create(node, SyncResponse.create(msg.getCommands()), SyncResponse.class);
			});
	}

	public RemoteEventDispatcher<SyncRequest> syncRequestDispatcher() {
		return this::sendSyncRequest;
	}

	private void sendSyncRequest(BFTNode node, SyncRequest syncRequest) {
		addressBook.peer(node.getKey().euid()).ifPresent(peer -> {
			if (peer.hasSystem()) {
				final var msg = new SyncRequestMessage(this.magic, syncRequest.getHeader());
				this.messageCentral.send(peer, msg);
			}
		});
	}

	public RemoteEventDispatcher<SyncResponse> syncResponseDispatcher() {
		return this::sendSyncResponse;
	}

	private void sendSyncResponse(BFTNode node, SyncResponse syncResponse) {
		addressBook.peer(node.getKey().euid()).ifPresent(peer -> {
			if (peer.hasSystem()) {
				final var msg = new SyncResponseMessage(this.magic, syncResponse.getCommandsAndProof());
				this.messageCentral.send(peer, msg);
			}
		});
	}

	public RemoteEventDispatcher<StatusRequest> statusRequestDispatcher() {
		return this::sendStatusRequest;
	}

	private void sendStatusRequest(BFTNode node, StatusRequest statusRequest) {
		addressBook.peer(node.getKey().euid()).ifPresent(peer -> {
			if (peer.hasSystem()) {
				final var msg = new StatusRequestMessage(this.magic);
				this.messageCentral.send(peer, msg);
			}
		});
	}

	public RemoteEventDispatcher<StatusResponse> statusResponseDispatcher() {
		return this::sendStatusResponse;
	}

	private void sendStatusResponse(BFTNode node, StatusResponse statusResponse) {
		addressBook.peer(node.getKey().euid()).ifPresent(peer -> {
			if (peer.hasSystem()) {
				final var msg = new StatusResponseMessage(this.magic, statusResponse.getHeader());
				this.messageCentral.send(peer, msg);
			}
		});
	}
}
