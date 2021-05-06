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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry;
import com.radixdlt.network.p2p.PeerEvent.PeerConnected;
import com.radixdlt.network.p2p.PeerEvent.PeerDisconnected;
import com.radixdlt.network.p2p.PeerEvent.PeerLostLiveness;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.utils.functional.Result;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.radixdlt.network.messaging.MessagingErrors.OUTBOUND_CHANNELS_LIMIT_REACHED;
import static com.radixdlt.network.messaging.MessagingErrors.SELF_CONNECTION_ATTEMPT;
import static java.util.function.Predicate.not;

/**
 * Manages active connections to other peers.
 */
@Singleton
public final class PeerManager {
	private static final Logger log = LogManager.getLogger();

	private final NodeId self;
	private final P2PConfig config;
	private final AddressBook addressBook;
	private final Provider<PendingOutboundChannelsManager> pendingOutboundChannelsManager;

	private final Object lock = new Object();
	private final Map<NodeId, Set<PeerChannel>> activeChannels = new HashMap<>();
	private final PublishSubject<Observable<InboundMessage>> inboundMessagesFromChannels = PublishSubject.create();

	@Inject
	public PeerManager(
		@Self BFTNode self,
		P2PConfig config,
		AddressBook addressBook,
		Provider<PendingOutboundChannelsManager> pendingOutboundChannelsManager
	) {
		this.self = Objects.requireNonNull(NodeId.fromPublicKey(self.getKey()));
		this.config = Objects.requireNonNull(config);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.pendingOutboundChannelsManager = Objects.requireNonNull(pendingOutboundChannelsManager);
	}

	public Observable<InboundMessage> messages() {
		return Observable.merge(inboundMessagesFromChannels);
	}

	public CompletableFuture<PeerChannel> findOrCreateChannel(NodeId nodeId) {
		synchronized (lock) {
			final var checkResult = this.canConnectTo(nodeId);
			return checkResult.fold(
				error -> CompletableFuture.failedFuture(new RuntimeException(error.message())),
				unused -> this.findOrCreateChannelInternal(nodeId)
			);
		}
	}

	private CompletableFuture<PeerChannel> findOrCreateChannelInternal(NodeId nodeId) {
		final var maybeActiveChannel = channelFor(nodeId);
		if (maybeActiveChannel.isPresent()) {
			return CompletableFuture.completedFuture(maybeActiveChannel.get());
		} else {
			final var maybeAddress = this.addressBook.findBestKnownAddressById(nodeId);
			if (maybeAddress.isPresent()) {
				return connect(maybeAddress.get());
			} else {
				return CompletableFuture.failedFuture(new RuntimeException("Unknown peer " + nodeId));
			}
		}
	}

	/**
	 * Try connecting to a specific URI
	 */
	public void tryConnect(RadixNodeUri uri) {
		synchronized (lock) {
			if (!canConnectTo(uri.getNodeId()).isSuccess()) {
				return;
			}

			if (this.getRemainingOutboundSlots() <= 0) {
				return;
			}

			if (channelFor(uri.getNodeId()).isEmpty()) {
				this.connect(uri);
			}
		}
	}

	private Result<Object> canConnectTo(NodeId nodeId) {
		if (nodeId.equals(self)) {
			log.info("Ignoring self connection attempt");
			return SELF_CONNECTION_ATTEMPT.result();
		}

		if (this.getRemainingOutboundSlots() <= 0) {
			return OUTBOUND_CHANNELS_LIMIT_REACHED.result();
		}

		return Result.ok(new Object());
	}

	private Optional<PeerChannel> channelFor(NodeId nodeId) {
		return Optional.ofNullable(this.activeChannels.get(nodeId))
			.stream().map(s -> s.iterator().next())
			.findAny();
	}

	// TODO(luk): update address book with the URIs that have been tried, but conn failed
	private CompletableFuture<PeerChannel> connect(RadixNodeUri uri) {
		synchronized (lock) {
			return channelFor(uri.getNodeId())
				.map(CompletableFuture::completedFuture) // either return an existing channel
				.orElseGet(() -> this.pendingOutboundChannelsManager.get().connectTo(uri)); // or try to create a new one
		}
	}

	public EventProcessor<PeerEvent> peerEventProcessor() {
		return peerEvent -> {
			if (peerEvent instanceof PeerConnected) {
				this.handlePeerConnected((PeerConnected) peerEvent);
			} else if (peerEvent instanceof PeerDisconnected) {
				this.handlePeerDisconnected((PeerDisconnected) peerEvent);
			} else if (peerEvent instanceof PeerLostLiveness) {
				this.handlePeerLostLiveness((PeerLostLiveness) peerEvent);
			}
		};
	}

	private void handlePeerConnected(PeerConnected peerConnected) {
		synchronized (lock) {
			final var channel = peerConnected.getChannel();
			final var channels = this.activeChannels.computeIfAbsent(
				channel.getRemoteNodeId(),
				unused -> new HashSet<>()
			);
			channels.add(channel);
			channel.getUri().ifPresent(this.addressBook::addOrUpdateSuccessfullyConnectedPeer);
			inboundMessagesFromChannels.onNext(channel.inboundMessages().toObservable());

			if (channel.isInbound() && !this.shouldAcceptInboundPeer(channel.getRemoteNodeId())) {
				channel.disconnect();
			}
		}
	}

	private boolean shouldAcceptInboundPeer(NodeId nodeId) {
		final var isBanned = this.addressBook.findById(nodeId)
			.map(AddressBookEntry::isBanned)
			.orElse(false);

		final var limitReached = this.activeChannels.size() > config.maxInboundChannels();

		return !isBanned && !limitReached;
	}

	private void handlePeerDisconnected(PeerDisconnected peerDisconnected) {
		synchronized (lock) {
			final var channel = peerDisconnected.getChannel();
			final var channelsForPubKey = this.activeChannels.get(channel.getRemoteNodeId());
			if (channelsForPubKey != null) {
				channelsForPubKey.remove(channel);
				if (channelsForPubKey.isEmpty()) {
					this.activeChannels.remove(channel.getRemoteNodeId());
				}
			}
		}
	}

	private void handlePeerLostLiveness(PeerLostLiveness peerLostLiveness) {
		synchronized (lock) {
			log.info("Peer {} lost liveness (ping timeout)", peerLostLiveness.getNodeId());
			channelFor(peerLostLiveness.getNodeId())
				.ifPresent(PeerChannel::disconnect);
			// TODO(luk): also update address book, reduce "score" or set some flag
		}
	}

	public ImmutableSet<PeerChannel> activePeers() {
		return this.activeChannels.values().stream()
			.flatMap(Collection::stream)
			.collect(ImmutableSet.toImmutableSet());
	}

	public boolean isPeerConnected(NodeId nodeId) {
		return this.activeChannels.containsKey(nodeId);
	}

	public int getRemainingOutboundSlots() {
		final var numChannels = this.activeChannels.values().stream()
			.flatMap(Set::stream)
			.filter(not(PeerChannel::isInbound))
			.count();

		return (int) Math.max(0, config.maxOutboundChannels() - numChannels);
	}
}
