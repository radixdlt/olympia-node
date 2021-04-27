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

import com.google.inject.name.Named;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.liveness.Ping;
import com.radixdlt.network.p2p.liveness.Pong;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Network interface for syncing committed state using the MessageCentral
 */
@Singleton
public final class MessageCentralPeerLiveness {
	private final int magic;
	private final MessageCentral messageCentral;

	@Inject
	public MessageCentralPeerLiveness(
		@Named("magic") int magic,
		MessageCentral messageCentral
	) {
		this.magic = magic;
		this.messageCentral = Objects.requireNonNull(messageCentral);
	}

	public Flowable<RemoteEvent<Ping>> pings() {
		return this.messageCentral.messagesOf(PeerPingMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				return RemoteEvent.create(node, Ping.create());
			});
	}

	public Flowable<RemoteEvent<Pong>> pongs() {
		return this.messageCentral.messagesOf(PeerPongMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				return RemoteEvent.create(node, Pong.create());
			});
	}

	public RemoteEventDispatcher<Ping> pingDispatcher() {
		return this::sendPing;
	}

	private void sendPing(BFTNode node, Ping ping) {
		final var msg = new PeerPingMessage(this.magic);
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}

	public RemoteEventDispatcher<Pong> pongDispatcher() {
		return this::sendPong;
	}

	private void sendPong(BFTNode node, Pong pong) {
		final var msg = new PeerPongMessage(this.magic);
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}
}
