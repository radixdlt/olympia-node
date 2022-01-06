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

package com.radixdlt.network.p2p.proxy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.NetworkId;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.time.Time;

/**
 * Manages the proxy certificates: - issued by this node - granted to this node - received from
 * other peers
 */
public final class ProxyCertificateManager {
  private static final Logger log = LogManager.getLogger();

  private final NodeId self;
  private final P2PConfig.ProxyConfig config;
  private final int networkId;
  private final Addressing addressing;
  private final ECKeyOps ecKeyOps;
  private final PeersView peersView;
  private final PeerControl peerControl;
  private final AddressBook addressBook;
  private final RemoteEventDispatcher<GrantedProxyCertificate> grantedProxyCertificateDispatcher;
  private final RemoteEventDispatcher<ProxyCertificatesAnnouncement>
      proxyCertificatesAnnouncementDispatcher;
  private final Object lock = new Object();

  // the certificates that were granted to this node
  private final Map<NodeId, ProxyCertificate> receivedProxyCertificates = new ConcurrentHashMap<>();

  // the certificates issued by this node
  private final Map<NodeId, ProxyCertificate> issuedProxyCertificates = new ConcurrentHashMap<>();

  // the certificates received from other nodes (granted to them)
  private final Map<NodeId, Set<VerifiedProxyCertificate>> verifiedProxies =
      new ConcurrentHashMap<>();

  @Inject
  public ProxyCertificateManager(
      @Self NodeId self,
      P2PConfig.ProxyConfig config,
      @NetworkId int networkId,
      Addressing addressing,
      ECKeyOps ecKeyOps,
      PeersView peersView,
      PeerControl peerControl,
      AddressBook addressBook,
      RemoteEventDispatcher<GrantedProxyCertificate> grantedProxyCertificateDispatcher,
      RemoteEventDispatcher<ProxyCertificatesAnnouncement>
          proxyCertificatesAnnouncementDispatcher) {
    this.self = Objects.requireNonNull(self);
    this.config = Objects.requireNonNull(config);
    this.networkId = networkId;
    this.addressing = Objects.requireNonNull(addressing);
    this.ecKeyOps = Objects.requireNonNull(ecKeyOps);
    this.peersView = Objects.requireNonNull(peersView);
    this.peerControl = Objects.requireNonNull(peerControl);
    this.addressBook = Objects.requireNonNull(addressBook);
    this.grantedProxyCertificateDispatcher =
        Objects.requireNonNull(grantedProxyCertificateDispatcher);
    this.proxyCertificatesAnnouncementDispatcher =
        Objects.requireNonNull(proxyCertificatesAnnouncementDispatcher);
  }

  public ImmutableSet<ProxyCertificate> getReceivedProxyCertificates() {
    return ImmutableSet.copyOf(receivedProxyCertificates.values());
  }

  public ImmutableSet<NodeId> getVerifiedProxiesForNode(NodeId nodeId) {
    return verifiedProxies.getOrDefault(nodeId, Set.of()).stream()
        .filter(cert -> cert.expiresAt() > Time.currentTimestamp())
        .map(VerifiedProxyCertificate::grantee)
        .collect(ImmutableSet.toImmutableSet());
  }

  public void handlePeerConnected(PeerEvent.PeerConnected peerConnected) {
    synchronized (lock) {
      final var peerNodeId = peerConnected.getChannel().getRemoteNodeId();

      verifyAndUpdateProxyCertificates(peerNodeId, peerConnected.getChannel().proxyCertificates());

      if (config.authorizedProxies().contains(peerNodeId)) {
        handleProxyCertificateIssuance(peerNodeId);
      }
    }
  }

  private void verifyAndUpdateProxyCertificates(
      NodeId peer, ImmutableSet<ProxyCertificate> proxyCertificates) {
    final var verifiedCertificates =
        proxyCertificates.stream()
            .map(cert -> this.verifyProxyCertificate(peer, cert))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(ImmutableSet.toImmutableSet());

    if (verifiedCertificates.size() == proxyCertificates.size()) {
      verifiedCertificates.forEach(
          verifiedCertificate -> {
            final var verifiedChannelsForSigner =
                this.verifiedProxies.computeIfAbsent(
                    verifiedCertificate.signer(), unused -> Sets.newConcurrentHashSet());
            verifiedChannelsForSigner.add(verifiedCertificate);
          });
      this.addressBook.updateProxyCertificates(peer, verifiedCertificates);
    } else {
      final var banDuration = Duration.ofMinutes(15);
      log.warn(
          "Received invalid proxy certificate from {}, banning for {}",
          addressing.forNodes().of(peer.getPublicKey()),
          banDuration);
      peerControl.banPeer(peer, banDuration, "Received invalid proxy certificate");
    }
  }

  public EventProcessor<RenewIssuedProxyCertificatesTrigger>
      renewIssuedProxyCertificatesTriggerProcessor() {
    return unused -> {
      // renew if cert expired or is about to expire in less than a minute
      final var minExpirationTime = Instant.now().plus(Duration.ofMinutes(1));
      final var certsToRenew =
          this.issuedProxyCertificates.entrySet().stream()
              .filter(
                  e ->
                      minExpirationTime.isAfter(
                          Instant.ofEpochMilli(e.getValue().getData().expiresAt())))
              .map(Map.Entry::getKey)
              .collect(ImmutableSet.toImmutableSet());

      certsToRenew.forEach(this::handleProxyCertificateIssuance);
    };
  }

  private Optional<VerifiedProxyCertificate> verifyProxyCertificate(
      NodeId expectedGrantee, ProxyCertificate proxyCertificate) {
    return proxyCertificate
        .verify()
        .filter(cert -> cert.grantee().equals(expectedGrantee))
        .filter(cert -> cert.networkId() == networkId);
  }

  private void handleProxyCertificateIssuance(NodeId nodeId) {
    log.debug(
        "Issuing a proxy certificate for {}", addressing.forNodes().of(nodeId.getPublicKey()));
    final var cert = issueProxyCertificate(nodeId, config.issuedProxyCertificateValidityDuration());
    this.issuedProxyCertificates.put(nodeId, cert);
    grantedProxyCertificateDispatcher.dispatch(
        BFTNode.create(nodeId.getPublicKey()), new GrantedProxyCertificate(cert));
  }

  private ProxyCertificate issueProxyCertificate(NodeId nodeId, Duration validityDuration) {
    final var expiresAt = Instant.now().plus(validityDuration).toEpochMilli();
    final var data = ProxyCertificateData.create(nodeId, expiresAt, networkId);
    final var signature = ecKeyOps.sign(data.hashToSign().asBytes());
    return ProxyCertificate.create(data, signature);
  }

  public RemoteEventProcessor<GrantedProxyCertificate> grantedProxyCertificateEventProcessor() {
    return (peer, ev) -> {
      synchronized (lock) {
        if (!config.proxyEnabled()) {
          return; // ignore if proxy not enabled
        }

        if (!config.authorizedProxiedPeers().contains(NodeId.fromPublicKey(peer.getKey()))) {
          return; // peer is not an authorized proxied node; ignore
        }

        verifyProxyCertificate(self, ev.proxyCertificate())
            .ifPresentOrElse(
                verifiedCert ->
                    handleVerifiedGrantedProxyCertificate(ev.proxyCertificate(), verifiedCert),
                () -> handleInvalidGrantedProxyCertificate(NodeId.fromPublicKey(peer.getKey())));
      }
    };
  }

  private void handleVerifiedGrantedProxyCertificate(
      ProxyCertificate unverifiedCert, VerifiedProxyCertificate verifiedCert) {
    log.trace("Received a granted proxy certificate");

    final var existingCertExpiration =
        Optional.ofNullable(this.receivedProxyCertificates.get(verifiedCert.signer()))
            .map(cert -> cert.getData().expiresAt())
            .orElse(0L);

    if (verifiedCert.expiresAt() <= existingCertExpiration) {
      return; // certificate expires earlier that the one we currently have; ignore
    }

    this.receivedProxyCertificates.put(verifiedCert.signer(), unverifiedCert);

    // announce the certificate to connected peers
    this.proxyCertificatesAnnouncementDispatcher.dispatch(
        peersView.peers().map(p -> BFTNode.create(p.getNodeId().getPublicKey())).toList(),
        new ProxyCertificatesAnnouncement(ImmutableSet.copyOf(receivedProxyCertificates.values())));
  }

  private void handleInvalidGrantedProxyCertificate(NodeId peer) {
    final var banDuration = Duration.ofSeconds(30);
    log.warn(
        "Received invalid granted proxy certificate from {}, banning for {}",
        addressing.forNodes().of(peer.getPublicKey()),
        banDuration);
    peerControl.banPeer(peer, banDuration, "Received invalid granted proxy certificate");
  }

  public RemoteEventProcessor<ProxyCertificatesAnnouncement>
      proxyCertificatesAnnouncementEventProcessor() {
    return (peer, ev) ->
        verifyAndUpdateProxyCertificates(
            NodeId.fromPublicKey(peer.getKey()), ev.proxyCertificates());
  }

  public void handlePeerDisconnected(PeerEvent.PeerDisconnected peerDisconnected) {
    synchronized (lock) {
      final var channel = peerDisconnected.getChannel();
      final var mapEntryIter = this.verifiedProxies.entrySet().iterator();
      while (mapEntryIter.hasNext()) {
        final var certs = mapEntryIter.next().getValue();
        certs.removeIf(cert -> cert.grantee().equals(channel.getRemoteNodeId()));
        if (certs.isEmpty()) {
          mapEntryIter.remove();
        }
      }

      this.issuedProxyCertificates.remove(peerDisconnected.getChannel().getRemoteNodeId());
    }
  }
}
