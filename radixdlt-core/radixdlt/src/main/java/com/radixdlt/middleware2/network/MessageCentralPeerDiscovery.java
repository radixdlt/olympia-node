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
import com.radixdlt.network.p2p.discovery.GetPeers;
import com.radixdlt.network.p2p.discovery.PeersResponse;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.radix.network.messages.GetPeersMessage;
import org.radix.network.messages.PeersResponseMessage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;

/**
 * Network interface for peer discovery messages the MessageCentral
 */
@Singleton
public final class MessageCentralPeerDiscovery {
	private final int magic;
	private final MessageCentral messageCentral;

	@Inject
	public MessageCentralPeerDiscovery(
		@Named("magic") int magic,
		MessageCentral messageCentral
	) {
		this.magic = magic;
		this.messageCentral = Objects.requireNonNull(messageCentral);
	}

	public Flowable<RemoteEvent<GetPeers>> getPeersEvents() {
		return this.messageCentral.messagesOf(GetPeersMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				return RemoteEvent.create(node, GetPeers.create(), GetPeers.class);
			});
	}

	public Flowable<RemoteEvent<PeersResponse>> peersResponses() {
		return this.messageCentral.messagesOf(PeersResponseMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				return RemoteEvent.create(node, PeersResponse.create(m.getMessage().getPeers()), PeersResponse.class);
			});
	}

	public RemoteEventDispatcher<GetPeers> getPeersDispatcher() {
		return this::sendGetPeers;
	}

	private void sendGetPeers(BFTNode node, GetPeers getPeers) {
		final var msg = new GetPeersMessage(this.magic);
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}

	public RemoteEventDispatcher<PeersResponse> peersResponseDispatcher() {
		return this::sendPeersResponse;
	}

	private void sendPeersResponse(BFTNode node, PeersResponse peersResponse) {
		final var msg = new PeersResponseMessage(this.magic, peersResponse.getPeers());
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}
}
