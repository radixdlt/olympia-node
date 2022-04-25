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

import static com.radixdlt.monitoring.SystemCounters.CounterType.*;
import static com.radixdlt.network.messaging.MessagingErrors.PEER_BANNED;
import static com.radixdlt.network.messaging.MessagingErrors.SELF_CONNECTION_ATTEMPT;
import static com.radixdlt.utils.functional.Tuple.unitResult;
import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.p2p.PeerEvent.PeerBanned;
import com.radixdlt.network.p2p.PeerEvent.PeerConnected;
import com.radixdlt.network.p2p.PeerEvent.PeerConnectionTimeout;
import com.radixdlt.network.p2p.PeerEvent.PeerDisconnected;
import com.radixdlt.network.p2p.PeerEvent.PeerHandshakeFailed;
import com.radixdlt.network.p2p.PeerEvent.PeerLostLiveness;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.Lists;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple.Unit;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Manages active connections to other peers. */
public final class PeerManager {
  private static final Logger log = LogManager.getLogger();

  private static final int MAX_URIS_TO_TRY_FOR_SINGLE_CONNECT_TRIGGER = 5;

  private final NodeId self;
  private final P2PConfig config;
  private final Addressing addressing;
  private final Provider<AddressBook> addressBook;
  private final Provider<PendingOutboundChannelsManager> pendingOutboundChannelsManager;
  private final SystemCounters counters;
  private final Object lock = new Object();
  private final Map<NodeId, Set<PeerChannel>> activeChannels = new ConcurrentHashMap<>();
  private final PublishSubject<Observable<InboundMessage>> inboundMessagesFromChannels =
      PublishSubject.create();

  @Inject
  public PeerManager(
      @Self RadixNodeUri self,
      P2PConfig config,
      Addressing addressing,
      Provider<AddressBook> addressBook,
      Provider<PendingOutboundChannelsManager> pendingOutboundChannelsManager,
      SystemCounters counters) {
    this.self = Objects.requireNonNull(self.getNodeId());
    this.config = Objects.requireNonNull(config);
    this.addressing = addressing;
    this.addressBook = Objects.requireNonNull(addressBook);
    this.pendingOutboundChannelsManager = Objects.requireNonNull(pendingOutboundChannelsManager);
    this.counters = Objects.requireNonNull(counters);

    log.info("Node URI: {}", self);
  }

  public Observable<InboundMessage> messages() {
    return Observable.merge(inboundMessagesFromChannels);
  }

  public CompletableFuture<PeerChannel> findOrCreateChannel(NodeId nodeId) {
    synchronized (lock) {
      final var checkResult = this.canConnectTo(nodeId);
      return checkResult.fold(
          error -> CompletableFuture.failedFuture(new RuntimeException(error.message())),
          unused -> this.findOrCreateChannelInternal(nodeId));
    }
  }

  private CompletableFuture<PeerChannel> findOrCreateChannelInternal(NodeId nodeId) {
    final var maybeActiveChannel = channelFor(nodeId);
    if (maybeActiveChannel.isPresent()) {
      return CompletableFuture.completedFuture(maybeActiveChannel.get());
    } else {
      final var addresses = this.addressBook.get().bestKnownAddressesById(nodeId);
      return tryConnectWithRetries(addresses, MAX_URIS_TO_TRY_FOR_SINGLE_CONNECT_TRIGGER);
    }
  }

  private CompletableFuture<PeerChannel> tryConnectWithRetries(
      ImmutableList<RadixNodeUri> remainingAddresses, int triesLeft) {
    if (remainingAddresses.isEmpty() || triesLeft <= 0) {
      return CompletableFuture.failedFuture(
          new RuntimeException("No valid address available for peer"));
    } else {
      final var nextAddr = remainingAddresses.get(0);
      final var channelFuture = connect(nextAddr);
      return channelFuture.exceptionallyCompose(
          ex -> tryConnectWithRetries(Lists.tail(remainingAddresses), triesLeft - 1));
    }
  }

  /** Try connecting to a specific URI */
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

  private Result<Unit> canConnectTo(NodeId nodeId) {
    if (nodeId.equals(self)) {
      log.info("Ignoring self connection attempt");
      return SELF_CONNECTION_ATTEMPT.result();
    }

    if (this.addressBook.get().findById(nodeId).filter(AddressBookEntry::isBanned).isPresent()) {
      return PEER_BANNED.result();
    }

    return unitResult();
  }

  private Optional<PeerChannel> channelFor(NodeId nodeId) {
    return Optional.ofNullable(this.activeChannels.get(nodeId)).stream()
        .map(channelSet -> channelSet.iterator().next())
        .findAny();
  }

  private CompletableFuture<PeerChannel> connect(RadixNodeUri uri) {
    synchronized (lock) {
      return channelFor(uri.getNodeId())
          .map(CompletableFuture::completedFuture) // either return an existing channel
          .orElseGet(() -> tryConnectTo(uri)); // or try to create a new one
    }
  }

  private CompletableFuture<PeerChannel> tryConnectTo(RadixNodeUri uri) {
    return this.pendingOutboundChannelsManager.get().connectTo(uri);
  }

  public EventProcessor<PeerEvent> peerEventProcessor() {
    return peerEvent -> {
      if (peerEvent instanceof PeerConnected peerConnected) {
        this.handlePeerConnected(peerConnected);
      } else if (peerEvent instanceof PeerDisconnected peerDisconnected) {
        this.handlePeerDisconnected(peerDisconnected);
      } else if (peerEvent instanceof PeerLostLiveness peerLostLiveness) {
        this.handlePeerLostLiveness(peerLostLiveness);
      } else if (peerEvent instanceof PeerBanned peerBanned) {
        this.handlePeerBanned(peerBanned);
      } else if (peerEvent instanceof PeerConnectionTimeout peerConnectionTimeout) {
        this.handlePeerConnectionTimeout(peerConnectionTimeout);
      } else if (peerEvent instanceof PeerHandshakeFailed peerHandshakeFailed) {
        this.handlePeerHandshakeFailed(peerHandshakeFailed);
      }
    };
  }

  private void handlePeerConnected(PeerConnected peerConnected) {
    synchronized (lock) {
      final var channel = peerConnected.channel();
      final var channels =
          this.activeChannels.computeIfAbsent(
              channel.getRemoteNodeId(), unused -> Sets.newConcurrentHashSet());
      channels.add(channel);
      if (channel.isOutbound()) {
        channel.getUri().ifPresent(this.addressBook.get()::addOrUpdatePeerWithSuccessfulConnection);
      }
      inboundMessagesFromChannels.onNext(channel.inboundMessages().toObservable());

      if (channel.isInbound() && !this.shouldAcceptInboundPeer(channel.getRemoteNodeId())) {
        channel.disconnect();
      }

      if (channel.isOutbound() && this.getRemainingOutboundSlots() < 0) {
        // we're over the limit, need to disconnect one of the peers
        this.disconnectOutboundPeersOverLimit(peerConnected.channel().getRemoteNodeId());
      }

      updateChannelsCounters();
    }
  }

  private void disconnectOutboundPeersOverLimit(NodeId justConnectedPeer) {
    // TODO(luk): first try to disconnect duplicated channels (inbound)

    if (this.getRemainingOutboundSlots() >= 0) {
      return; // we're good
    }

    final var peersOverLimit = -this.getRemainingOutboundSlots();

    final Comparator<PeerChannel> comparator =
        (p1, p2) -> (int) (p1.sentMessagesRate() - p2.sentMessagesRate());

    this.activeChannels().stream()
        // not disconnecting peer that has just connected
        .filter(not(p -> p.getRemoteNodeId().equals(justConnectedPeer)))
        .sorted(comparator)
        .limit(peersOverLimit)
        .forEach(PeerChannel::disconnect);
  }

  private boolean shouldAcceptInboundPeer(NodeId nodeId) {
    final boolean isBanned =
        this.addressBook.get().findById(nodeId).map(AddressBookEntry::isBanned).orElse(false);

    if (isBanned) {
      log.info("Dropping inbound connection from peer {}: peer is banned", nodeAddress(nodeId));
    }

    final var limitReached = this.activeChannels.size() > config.maxInboundChannels();
    if (limitReached) {
      log.info(
          "Dropping inbound connection from peer {}: no inbound channels left",
          nodeAddress(nodeId));
    }

    return !isBanned && !limitReached;
  }

  private void handlePeerDisconnected(PeerDisconnected peerDisconnected) {
    synchronized (lock) {
      final var channel = peerDisconnected.channel();
      final var channelsForPubKey = this.activeChannels.get(channel.getRemoteNodeId());

      if (channelsForPubKey != null) {
        channelsForPubKey.remove(channel);
        if (channelsForPubKey.isEmpty()) {
          this.activeChannels.remove(channel.getRemoteNodeId());
          updateChannelsCounters();
        }
      }
    }
  }

  private void handlePeerLostLiveness(PeerLostLiveness peerLostLiveness) {
    synchronized (lock) {
      var nodeAddress = nodeAddress(peerLostLiveness.nodeId());

      log.info("Peer {} lost liveness (ping timeout)", nodeAddress);
      channelFor(peerLostLiveness.nodeId()).ifPresent(PeerChannel::disconnect);
    }
  }

  public ImmutableSet<PeerChannel> activeChannels() {
    return this.activeChannels.values().stream()
        .flatMap(Collection::stream)
        .collect(ImmutableSet.toImmutableSet());
  }

  public boolean isPeerConnected(NodeId nodeId) {
    return this.activeChannels.containsKey(nodeId);
  }

  public int getRemainingOutboundSlots() {
    final var numChannels =
        this.activeChannels.values().stream()
            .flatMap(Set::stream)
            .filter(not(PeerChannel::isInbound))
            .count();

    return (int) (config.maxOutboundChannels() - numChannels);
  }

  private void handlePeerBanned(PeerBanned event) {
    this.activeChannels().stream()
        .filter(peerChannel -> isSameNodeId(peerChannel, event.nodeId()))
        .forEach(this::handlePeerBanned);
  }

  private boolean isSameNodeId(PeerChannel peerChannel, NodeId nodeId) {
    return peerChannel.getRemoteNodeId().equals(nodeId);
  }

  private void handlePeerBanned(PeerChannel peerChannel) {
    var nodeAddress = nodeAddress(peerChannel.getRemoteNodeId());

    log.info("Closing channel to peer {} because peer has been banned", nodeAddress);
    peerChannel.disconnect();
  }

  private void handlePeerConnectionTimeout(PeerConnectionTimeout peerConnectionTimeout) {
    this.addressBook.get().addOrUpdatePeerWithFailedConnection(peerConnectionTimeout.uri());
  }

  private void handlePeerHandshakeFailed(PeerHandshakeFailed peerHandshakeFailed) {
    peerHandshakeFailed.channel().getUri().ifPresent(this.addressBook.get()::blacklist);
  }

  private void updateChannelsCounters() {
    long inboundCount = activeChannels().stream().filter(PeerChannel::isInbound).count();
    long outboundCount = activeChannels().stream().filter(PeerChannel::isOutbound).count();

    counters.set(NETWORKING_P2P_ACTIVE_CHANNELS, activeChannels().size());
    counters.set(NETWORKING_P2P_ACTIVE_INBOUND_CHANNELS, inboundCount);
    counters.set(NETWORKING_P2P_ACTIVE_OUTBOUND_CHANNELS, outboundCount);
  }

  private String nodeAddress(NodeId nodeId) {
    return addressing.forNodes().of(nodeId.getPublicKey());
  }
}
