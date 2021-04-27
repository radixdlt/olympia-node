/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.p2p.liveness;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.PeerEvent.PeerLostLiveness;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeersView;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Periodically pings peers and awaits for pong response
 * if pong is not received on time then it fires a PeerLostLiveness event.
 */
@Singleton
public final class PeerLivenessMonitor {
	private final P2PConfig config;
	private final PeersView peersView;
	private final EventDispatcher<PeerEvent> peerEventDispatcher;
	private final RemoteEventDispatcher<Ping> pingEventDispatcher;
	private final RemoteEventDispatcher<Pong> pongEventDispatcher;
	private final ScheduledEventDispatcher<PeerPingTimeout> pingTimeoutEventDispatcher;

	private final Set<NodeId> waitingForPong = new HashSet<>();

	@Inject
	public PeerLivenessMonitor(
		P2PConfig config,
		PeersView peersView,
		EventDispatcher<PeerEvent> peerEventDispatcher,
		RemoteEventDispatcher<Ping> pingEventDispatcher,
		RemoteEventDispatcher<Pong> pongEventDispatcher,
		ScheduledEventDispatcher<PeerPingTimeout> pingTimeoutEventDispatcher
	) {
		this.config = Objects.requireNonNull(config);
		this.peersView = Objects.requireNonNull(peersView);
		this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
		this.pingEventDispatcher = Objects.requireNonNull(pingEventDispatcher);
		this.pongEventDispatcher = Objects.requireNonNull(pongEventDispatcher);
		this.pingTimeoutEventDispatcher = Objects.requireNonNull(pingTimeoutEventDispatcher);
	}

	public EventProcessor<PeersLivenessCheckTrigger> peersLivenessCheckTriggerEventProcessor() {
		return unused -> peersView.peers().forEach(this::pingPeer);
	}

	private void pingPeer(PeersView.PeerInfo peerInfo) {
		final var nodeId = peerInfo.getNodeId();

		if (this.waitingForPong.contains(nodeId)) {
			return; // already pinged
		}

		this.waitingForPong.add(nodeId);
		this.pingEventDispatcher.dispatch(BFTNode.create(nodeId.getPublicKey()), Ping.create());
		this.pingTimeoutEventDispatcher.dispatch(PeerPingTimeout.create(nodeId), config.pingTimeout());
	}

	public EventProcessor<PeerPingTimeout> pingTimeoutEventProcessor() {
		return timeout -> {
			System.out.println("PING TIMEOUT EVENT RECEIVED");
			final var waitingForPeer = this.waitingForPong.remove(timeout.getNodeId());
			if (waitingForPeer) {
				System.out.println("TRIGGERING LSOT LIVENESS EVENT");
				this.peerEventDispatcher.dispatch(PeerLostLiveness.create(timeout.getNodeId()));
			}
		};
	}

	public RemoteEventProcessor<Ping> pingRemoteEventProcessor() {
		return (sender, ping) -> {
			this.pongEventDispatcher.dispatch(sender, Pong.create());
		};
	}

	public RemoteEventProcessor<Pong> pongRemoteEventProcessor() {
		return (sender, pong) -> {
			final var nodeId = NodeId.fromPublicKey(sender.getKey());
			this.waitingForPong.remove(nodeId);
		};
	}
}
