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

package com.radixdlt.network.p2p;

import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.network.p2p.transport.PeerOutboundBootstrap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Singleton
public final class PendingOutboundChannelsManager {
	private final P2PConfig config;
	private final PeerOutboundBootstrap peerOutboundBootstrap;
	private final ScheduledEventDispatcher<PeerOutboundConnectionTimeout> timeoutEventDispatcher;

	private final Object lock = new Object();
	private Map<NodeId, CompletableFuture<PeerChannel>> pendingChannels = new HashMap<>();

	@Inject
	public PendingOutboundChannelsManager(
		P2PConfig config,
		PeerOutboundBootstrap peerOutboundBootstrap,
		ScheduledEventDispatcher<PeerOutboundConnectionTimeout> timeoutEventDispatcher
	) {
		this.config = Objects.requireNonNull(config);
		this.peerOutboundBootstrap = Objects.requireNonNull(peerOutboundBootstrap);
		this.timeoutEventDispatcher = Objects.requireNonNull(timeoutEventDispatcher);
	}

	public CompletableFuture<PeerChannel> connectTo(RadixNodeUri uri) {
		synchronized (lock) {
			final var remoteNodeId = uri.getNodeId();

			if (this.pendingChannels.containsKey(remoteNodeId)) {
				return this.pendingChannels.get(remoteNodeId);
			} else {
				final var channelFuture = new CompletableFuture<PeerChannel>();
				this.pendingChannels.put(remoteNodeId, channelFuture);
				this.peerOutboundBootstrap.initOutboundConnection(uri);
				this.timeoutEventDispatcher.dispatch(new PeerOutboundConnectionTimeout(uri), config.peerConnectionTimeout());
				return channelFuture;
			}
		}
	}

	public EventProcessor<PeerEvent> peerEventProcessor() {
		return peerEvent -> {
			if (peerEvent instanceof PeerEvent.PeerConnected) {
				this.handlePeerConnected((PeerEvent.PeerConnected) peerEvent);
			}
		};
	}

	private void handlePeerConnected(PeerEvent.PeerConnected peerConnected) {
		synchronized (lock) {
			final var channel = peerConnected.getChannel();
			final var maybeFuture = this.pendingChannels.remove(channel.getRemoteNodeId());
			if (maybeFuture != null) {
				maybeFuture.complete(channel);
			}
		}
	}

	public EventProcessor<PeerOutboundConnectionTimeout> peerOutboundConnectionTimeoutEventProcessor() {
		return timeout -> {
			synchronized (lock) {
				final var maybeFuture = this.pendingChannels.remove(timeout.getUri().getNodeId());
				if (maybeFuture != null) {
					maybeFuture.completeExceptionally(new RuntimeException("Peer connection timeout"));
				}
			}
		};
	}

	public static final class PeerOutboundConnectionTimeout {
		private final RadixNodeUri uri;

		public PeerOutboundConnectionTimeout(RadixNodeUri uri) {
			this.uri = uri;
		}

		public RadixNodeUri getUri() {
			return uri;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			final var that = (PeerOutboundConnectionTimeout) o;
			return Objects.equals(uri, that.uri);
		}

		@Override
		public int hashCode() {
			return Objects.hash(uri);
		}
	}
}
