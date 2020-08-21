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

import com.google.common.collect.ImmutableList;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.sync.StateSyncNetwork;
import com.radixdlt.sync.SyncRequest;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

/**
 * Network interface for syncing committed state using the MessageCentral
 */
public final class MessageCentralLedgerSync implements StateSyncNetwork {
	private final int magic;
	private final MessageCentral messageCentral;

	@Inject
	public MessageCentralLedgerSync(
		Universe universe,
		MessageCentral messageCentral
	) {
		this.magic = universe.getMagic();
		this.messageCentral = Objects.requireNonNull(messageCentral);
	}

	@Override
	public Observable<ImmutableList<CommittedCommand>> syncResponses() {
		return Observable.create(emitter -> {
			MessageListener<SyncResponseMessage> listener = (src, msg) -> emitter.onNext(msg.getCommands());
			this.messageCentral.addListener(SyncResponseMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		});
	}

	@Override
	public Observable<SyncRequest> syncRequests() {
		return Observable.create(emitter -> {
			MessageListener<SyncRequestMessage> listener = (src, msg) -> emitter.onNext(new SyncRequest(src, msg.getStateVersion()));
			this.messageCentral.addListener(SyncRequestMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		});
	}

	@Override
	public void sendSyncRequest(Peer peer, long stateVersion) {
		SyncRequestMessage syncRequestMessage = new SyncRequestMessage(this.magic, stateVersion);
		this.messageCentral.send(peer, syncRequestMessage);
	}

	@Override
	public void sendSyncResponse(Peer peer, List<CommittedCommand> commands) {
		final SyncResponseMessage syncResponseMessage = new SyncResponseMessage(this.magic, ImmutableList.copyOf(commands));
		this.messageCentral.send(peer, syncResponseMessage);
	}
}
