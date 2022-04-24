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

package com.radixdlt.network.p2p.discovery;

import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.networks.Addressing;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Discovers peers network addresses and adds them to the address book. Initial (seed) peers are
 * "discovered" from the config (bootstrapDiscovery) and more peers are requested from the peers
 * we're already connected to.
 */
public final class PeerDiscovery {
  private static final Logger log = LogManager.getLogger();

  private static final int MAX_PEERS_IN_RESPONSE = 50;
  private static final int MAX_REQUESTS_SENT_AT_ONCE = 5;

  private final RadixNodeUri selfUri;
  private final PeerManager peerManager;
  private final AddressBook addressBook;
  private final PeerControl peerControl;
  private final SeedNodesConfigParser seedNodesConfigParser;
  private final RemoteEventDispatcher<GetPeers> getPeersRemoteEventDispatcher;
  private final RemoteEventDispatcher<PeersResponse> peersResponseRemoteEventDispatcher;
  private final Addressing addressing;

  private final Set<NodeId> peersAsked = new HashSet<>();

  @Inject
  public PeerDiscovery(
      @Self RadixNodeUri selfUri,
      PeerManager peerManager,
      AddressBook addressBook,
      PeerControl peerControl,
      SeedNodesConfigParser seedNodesConfigParser,
      RemoteEventDispatcher<GetPeers> getPeersRemoteEventDispatcher,
      RemoteEventDispatcher<PeersResponse> peersResponseRemoteEventDispatcher,
      Addressing addressing) {
    this.selfUri = Objects.requireNonNull(selfUri);
    this.peerManager = Objects.requireNonNull(peerManager);
    this.addressBook = Objects.requireNonNull(addressBook);
    this.peerControl = Objects.requireNonNull(peerControl);
    this.seedNodesConfigParser = Objects.requireNonNull(seedNodesConfigParser);
    this.getPeersRemoteEventDispatcher = Objects.requireNonNull(getPeersRemoteEventDispatcher);
    this.peersResponseRemoteEventDispatcher =
        Objects.requireNonNull(peersResponseRemoteEventDispatcher);
    this.addressing = Objects.requireNonNull(addressing);
  }

  public EventProcessor<DiscoverPeers> discoverPeersEventProcessor() {
    return unused -> {
      final var seedNodes = seedNodesConfigParser.getResolvedSeedNodes();
      this.addressBook.addUncheckedPeers(seedNodes);

      final var channels = new ArrayList<>(this.peerManager.activeChannels());
      Collections.shuffle(channels);
      channels.stream()
          .filter(not(c -> peersAsked.contains(c.getRemoteNodeId())))
          .limit(MAX_REQUESTS_SENT_AT_ONCE)
          .forEach(
              peer -> {
                peersAsked.add(peer.getRemoteNodeId());
                getPeersRemoteEventDispatcher.dispatch(
                    BFTNode.create(peer.getRemoteNodeId().getPublicKey()), GetPeers.create());
              });

      this.tryConnectToSomeKnownPeers();
    };
  }

  private void tryConnectToSomeKnownPeers() {
    final var remainingSlots = this.peerManager.getRemainingOutboundSlots();
    final var maxSlotsToUse =
        Math.max(0, (remainingSlots / 2) - 2); // let's always leave some free slots
    this.addressBook
        .bestCandidatesToConnect()
        .filter(not(e -> peerManager.isPeerConnected(e.getNodeId())))
        .limit(maxSlotsToUse)
        .forEach(this.peerManager::tryConnect);
  }

  public RemoteEventProcessor<PeersResponse> peersResponseRemoteEventProcessor() {
    return (sender, peersResponse) -> {
      final var senderNodeId = NodeId.fromPublicKey(sender.getKey());
      if (!peersAsked.contains(senderNodeId)) {
        log.warn(
            "Received unexpected peers response from {}",
            addressing.forNodes().of(senderNodeId.getPublicKey()));
        this.peerControl.banPeer(senderNodeId, Duration.ofMinutes(15), "Unexpected peers response");
        return;
      }

      this.peersAsked.remove(senderNodeId);
      final var peersUpToLimit =
          peersResponse.getPeers().stream()
              .limit(MAX_PEERS_IN_RESPONSE)
              .collect(ImmutableSet.toImmutableSet());
      this.addressBook.addUncheckedPeers(peersUpToLimit);
    };
  }

  public RemoteEventProcessor<GetPeers> getPeersRemoteEventProcessor() {
    return (sender, unused) -> {
      final var peers =
          Stream.concat(
                  Stream.of(selfUri),
                  this.addressBook.bestCandidatesToConnect().limit(MAX_PEERS_IN_RESPONSE - 1))
              .collect(ImmutableSet.toImmutableSet());

      peersResponseRemoteEventDispatcher.dispatch(sender, PeersResponse.create(peers));
    };
  }
}
