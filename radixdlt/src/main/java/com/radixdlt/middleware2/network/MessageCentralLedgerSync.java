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
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.sync.RemoteSyncResponse;
import com.radixdlt.sync.StateSyncNetworkRx;
import com.radixdlt.sync.StateSyncNetworkSender;
import com.radixdlt.sync.RemoteSyncRequest;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.core.Observable;
import java.util.Objects;
import javax.inject.Inject;

/**
 * Network interface for syncing committed state using the MessageCentral
 */
public final class MessageCentralLedgerSync implements StateSyncNetworkSender, StateSyncNetworkRx {
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

	@Override
	public Observable<RemoteSyncResponse> syncResponses() {
		return Observable.create(emitter -> {
			MessageListener<SyncResponseMessage> listener = (src, msg) -> {
				if (src.hasSystem()) {
					BFTNode node = BFTNode.create(src.getSystem().getKey());
					emitter.onNext(new RemoteSyncResponse(node, msg.getCommands()));
				}
			};
			this.messageCentral.addListener(SyncResponseMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		});
	}

	@Override
	public Observable<RemoteSyncRequest> syncRequests() {
		return Observable.create(emitter -> {
			MessageListener<SyncRequestMessage> listener = (src, msg) -> {
				if (src.hasSystem()) {
					BFTNode node = BFTNode.create(src.getSystem().getKey());
					emitter.onNext(new RemoteSyncRequest(node, msg.getCurrentHeader()));
				}
			};
			this.messageCentral.addListener(SyncRequestMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
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

	@Override
	public void sendSyncResponse(BFTNode node, DtoCommandsAndProof commands) {
		addressBook.peer(node.getKey().euid()).ifPresent(peer -> {
			if (peer.hasSystem()) {
				final SyncResponseMessage syncResponseMessage = new SyncResponseMessage(this.magic, commands);
				this.messageCentral.send(peer, syncResponseMessage);
			}
		});
	}
}
