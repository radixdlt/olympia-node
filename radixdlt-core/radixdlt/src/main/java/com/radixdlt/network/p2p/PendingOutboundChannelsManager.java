/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.network.p2p;

import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.network.p2p.transport.PeerOutboundBootstrap;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
			} else if (peerEvent instanceof PeerEvent.PeerHandshakeFailed) {
				this.handlePeerHandshakeFailed((PeerEvent.PeerHandshakeFailed) peerEvent);
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

	private void handlePeerHandshakeFailed(PeerEvent.PeerHandshakeFailed peerHandshakeFailed) {
		synchronized (lock) {
			peerHandshakeFailed.getChannel().getUri().ifPresent(uri -> {
				final var maybeFuture = this.pendingChannels.remove(uri.getNodeId());
				if (maybeFuture != null) {
					maybeFuture.completeExceptionally(new IOException("Peer connection failed"));
				}
			});
		}
	}

	public EventProcessor<PeerOutboundConnectionTimeout> peerOutboundConnectionTimeoutEventProcessor() {
		return timeout -> {
			synchronized (lock) {
				final var maybeFuture = this.pendingChannels.remove(timeout.getUri().getNodeId());
				if (maybeFuture != null) {
					maybeFuture.completeExceptionally(new IOException("Peer connection timeout"));
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
