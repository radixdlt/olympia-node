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

package com.radixdlt.network.p2p.addressbook;

import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.PeerEvent.PeerBanned;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry.PeerAddressEntry;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry.PeerAddressEntry.LatestConnectionStatus;
import com.radixdlt.utils.InetUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Manages known peers network addresses and their metadata. */
public final class AddressBook {

  /**
   * A stateful comparator for known peer addresses that uses both their latest connection status
   * (persistent) and a number of failed connection attempts (volatile). Failure counts are used to
   * cycle the addresses for retries. The entries are sorted in the following manner: successful ->
   * unknown (no connection attempted yet) -> failed (by num of failures)
   */
  private static final class PeerAddressEntryComparator implements Comparator<PeerAddressEntry> {
    private final Map<RadixNodeUri, Integer> failureCounts = new HashMap<>();

    private int toIntValue(PeerAddressEntry peerAddressEntry) {
      return peerAddressEntry
          .getLatestConnectionStatus()
          .map(
              latestConnectionStatus -> {
                if (latestConnectionStatus == LatestConnectionStatus.SUCCESS) {
                  return 1;
                } else {
                  return -(1 + failureCounts.getOrDefault(peerAddressEntry.getUri(), 0));
                }
              })
          .orElse(0);
    }

    @Override
    public int compare(PeerAddressEntry a, PeerAddressEntry b) {
      return Integer.compare(toIntValue(b), toIntValue(a));
    }

    void incFailures(RadixNodeUri uri) {
      synchronized (this.failureCounts) {
        final var curr = this.failureCounts.getOrDefault(uri, 0);
        this.failureCounts.put(uri, curr + 1);
      }
    }

    void resetFailures(RadixNodeUri uri) {
      synchronized (this.failureCounts) {
        this.failureCounts.remove(uri);
      }
    }
  }

  private final RadixNodeUri self;
  private final P2PConfig p2pConfig;
  private final EventDispatcher<PeerEvent> peerEventDispatcher;
  private final AddressBookPersistence persistence;
  private final Object lock = new Object();
  private final Map<NodeId, AddressBookEntry> knownPeers = new ConcurrentHashMap<>();
  private final PeerAddressEntryComparator addressEntryComparator =
      new PeerAddressEntryComparator();

  @Inject
  public AddressBook(
      @Self RadixNodeUri self,
      P2PConfig p2pConfig,
      EventDispatcher<PeerEvent> peerEventDispatcher,
      AddressBookPersistence persistence) {
    this.self = Objects.requireNonNull(self);
    this.p2pConfig = Objects.requireNonNull(p2pConfig);
    this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
    this.persistence = Objects.requireNonNull(persistence);
    persistence.getAllEntries().forEach(e -> knownPeers.put(e.getNodeId(), e));
    cleanup();
  }

  // filters out the addresses with a different network ID that have been persisted before the
  // filtering was added
  private void cleanup() {
    final var cleanedUpEntries = new ImmutableMap.Builder<NodeId, AddressBookEntry>();
    this.knownPeers.values().forEach(entry -> cleanupAddressBookEntry(entry, cleanedUpEntries));
    this.knownPeers.clear();
    this.knownPeers.putAll(cleanedUpEntries.build());
  }

  private void cleanupAddressBookEntry(
      AddressBookEntry entry, ImmutableMap.Builder<NodeId, AddressBookEntry> cleanedUpEntries) {
    final var filteredKnownAddresses =
        entry.getKnownAddresses().stream()
            .filter(addr -> sameNetworkHrp(addr.getUri()))
            .collect(ImmutableSet.toImmutableSet());

    if (filteredKnownAddresses.isEmpty() && !entry.isBanned()) {
      // there are no known addresses and no ban info for peer so just remove it
      this.persistence.removeEntry(entry.getNodeId());
    } else if (filteredKnownAddresses.size() != entry.getKnownAddresses().size()) {
      // some addresses got filtered out, need to persist a new entry
      final var updatedEntry =
          new AddressBookEntry(entry.getNodeId(), entry.bannedUntil(), filteredKnownAddresses);
      cleanedUpEntries.put(entry.getNodeId(), updatedEntry);
      persistEntry(updatedEntry);
    } else {
      cleanedUpEntries.put(entry.getNodeId(), entry);
    }
  }

  public void addUncheckedPeers(Set<RadixNodeUri> peers) {
    final var filteredUris =
        peers.stream()
            .filter(not(uri -> uri.getNodeId().equals(this.self.getNodeId())))
            .filter(this::sameNetworkHrp)
            .filter(this::isPeerIpAddressValid)
            .collect(ImmutableList.toImmutableList());

    synchronized (lock) {
      filteredUris.forEach(this::insertOrUpdateAddressBookEntryWithUri);
    }
  }

  private void insertOrUpdateAddressBookEntryWithUri(RadixNodeUri uri) {
    final var maybeExistingEntry = this.knownPeers.get(uri.getNodeId());
    final var newOrUpdatedEntry =
        maybeExistingEntry == null
            ? AddressBookEntry.create(uri)
            : maybeExistingEntry.cleanupExpiredBlacklsitedUris().addUriIfNotExists(uri);

    if (!newOrUpdatedEntry.equals(maybeExistingEntry)) {
      upsertAddressBookEntry(uri.getNodeId(), newOrUpdatedEntry);
    }
  }

  private boolean sameNetworkHrp(RadixNodeUri uri) {
    return uri.getNetworkNodeHrp().equals(this.self.getNetworkNodeHrp());
  }

  public Optional<AddressBookEntry> findById(NodeId nodeId) {
    return Optional.ofNullable(this.knownPeers.get(nodeId));
  }

  public ImmutableList<RadixNodeUri> bestKnownAddressesById(NodeId nodeId) {
    final Optional<AddressBookEntry> addressBookEntryOpt;
    synchronized (lock) {
      addressBookEntryOpt = Optional.ofNullable(this.knownPeers.get(nodeId));
    }
    return onlyValidUrisSorted(addressBookEntryOpt.stream())
        .collect(ImmutableList.toImmutableList());
  }

  private Stream<RadixNodeUri> onlyValidUrisSorted(Stream<AddressBookEntry> entries) {
    return entries
        .filter(not(AddressBookEntry::isBanned))
        .flatMap(e -> e.getKnownAddresses().stream())
        .filter(not(PeerAddressEntry::blacklisted))
        .filter(addressBookEntry -> this.isPeerIpAddressValid(addressBookEntry.getUri()))
        .sorted(addressEntryComparator)
        .map(AddressBookEntry.PeerAddressEntry::getUri);
  }

  private boolean isPeerIpAddressValid(RadixNodeUri uri) {
    final InetAddress inetAddr;
    try {
      inetAddr = InetAddress.getByName(uri.getHost());
    } catch (UnknownHostException e) {
      return false;
    }

    // To filter out any local interface IP addresses (using the actual listen bind port)
    final var isLocalSelf =
        p2pConfig.listenPort() == uri.getPort() && InetUtils.isLocalAddress(inetAddr);

    // To filter out a public IP address (possibly running behind a NAT, hence using an advertised
    // "broadcast port")
    final var isPublicSelf =
        p2pConfig.broadcastPort() == uri.getPort() && self.getHost().equals(uri.getHost());

    return !isLocalSelf && !isPublicSelf;
  }

  public void addOrUpdatePeerWithSuccessfulConnection(RadixNodeUri radixNodeUri) {
    this.cleanupPeerEntriesFromRemoteHostPort(radixNodeUri.getHost(), radixNodeUri.getPort());
    this.addOrUpdatePeerWithLatestConnectionStatus(radixNodeUri, LatestConnectionStatus.SUCCESS);
    this.addressEntryComparator.resetFailures(radixNodeUri);
  }

  private void cleanupPeerEntriesFromRemoteHostPort(String host, int port) {
    synchronized (lock) {
      for (var entry : knownPeers.entrySet()) {
        calculateUpdatedEntry(entry.getValue(), host, port)
            .ifPresent(updatedEntry -> upsertAddressBookEntry(entry.getKey(), updatedEntry));
      }
    }
  }

  private Optional<AddressBookEntry> calculateUpdatedEntry(
      AddressBookEntry inputEntry, String host, int port) {

    var existingAddresses = inputEntry.getKnownAddresses();
    var filteredAddresses = filterMatchingAddresses(existingAddresses, host, port);

    if (filteredAddresses.size() == existingAddresses.size()) {
      return Optional.empty();
    }

    return Optional.of(inputEntry.withReplacedKnownAddresses(filteredAddresses));
  }

  private ImmutableSet<PeerAddressEntry> filterMatchingAddresses(
      ImmutableSet<PeerAddressEntry> knownAddresses, String host, int port) {
    return knownAddresses.stream()
        .filter(peerAddress -> doesNotMatchHostAndPort(peerAddress, host, port))
        .collect(ImmutableSet.toImmutableSet());
  }

  private boolean doesNotMatchHostAndPort(PeerAddressEntry peerAddress, String host, int port) {
    return peerAddress.getUri().getPort() != port || !peerAddress.getUri().getHost().equals(host);
  }

  public void addOrUpdatePeerWithFailedConnection(RadixNodeUri radixNodeUri) {
    this.addOrUpdatePeerWithLatestConnectionStatus(radixNodeUri, LatestConnectionStatus.FAILURE);
    this.addressEntryComparator.incFailures(radixNodeUri);
  }

  private void addOrUpdatePeerWithLatestConnectionStatus(
      RadixNodeUri radixNodeUri, LatestConnectionStatus latestConnectionStatus) {
    synchronized (lock) {
      final var maybeExistingEntry = this.knownPeers.get(radixNodeUri.getNodeId());
      final var entry =
          calculateNewOrUpdatedEntry(radixNodeUri, latestConnectionStatus, maybeExistingEntry);
      upsertAddressBookEntry(radixNodeUri.getNodeId(), entry);
    }
  }

  private void upsertAddressBookEntry(NodeId nodeId, AddressBookEntry entry) {
    this.knownPeers.put(nodeId, entry);
    persistEntry(entry);
  }

  private AddressBookEntry calculateNewOrUpdatedEntry(
      RadixNodeUri radixNodeUri,
      LatestConnectionStatus latestConnectionStatus,
      AddressBookEntry maybeExistingEntry) {
    return maybeExistingEntry == null
        ? AddressBookEntry.createWithLatestConnectionStatus(radixNodeUri, latestConnectionStatus)
        : maybeExistingEntry
            .cleanupExpiredBlacklsitedUris()
            .withLatestConnectionStatusForUri(radixNodeUri, latestConnectionStatus);
  }

  public Stream<RadixNodeUri> bestCandidatesToConnect() {
    return onlyValidUrisSorted(this.knownPeers.values().stream());
  }

  private void persistEntry(AddressBookEntry entry) {
    this.persistence.removeEntry(entry.getNodeId());
    this.persistence.saveEntry(entry);
  }

  void banPeer(NodeId nodeId, Duration banDuration) {
    synchronized (lock) {
      final var banUntil = Instant.now().plus(banDuration);
      final var maybeExistingEntry = findById(nodeId);
      if (maybeExistingEntry.isPresent()) {
        final var existingEntry = maybeExistingEntry.get();
        final var alreadyBanned =
            existingEntry.bannedUntil().filter(bu -> bu.isAfter(banUntil)).isPresent();
        if (!alreadyBanned) {
          final var updatedEntry =
              existingEntry.cleanupExpiredBlacklsitedUris().withBanUntil(banUntil);
          this.knownPeers.put(nodeId, updatedEntry);
          this.persistEntry(updatedEntry);
          this.peerEventDispatcher.dispatch(new PeerBanned(nodeId));
        }
      } else {
        final var newEntry = AddressBookEntry.createBanned(nodeId, banUntil);
        this.knownPeers.put(nodeId, newEntry);
        this.persistEntry(newEntry);
        this.peerEventDispatcher.dispatch(new PeerBanned(nodeId));
      }
    }
  }

  public ImmutableMap<NodeId, AddressBookEntry> knownPeers() {
    return ImmutableMap.copyOf(knownPeers);
  }

  public void blacklist(RadixNodeUri uri) {
    synchronized (lock) {
      final var blacklistUntil = Instant.now().plus(Duration.ofMinutes(30));
      final var maybeExistingEntry = this.knownPeers.get(uri.getNodeId());
      final var newOrUpdatedEntry =
          maybeExistingEntry == null
              ? AddressBookEntry.createBlacklisted(uri, blacklistUntil)
              : maybeExistingEntry
                  .cleanupExpiredBlacklsitedUris()
                  .withBlacklistedUri(uri, blacklistUntil);

      upsertAddressBookEntry(uri.getNodeId(), newOrUpdatedEntry);
    }
  }

  public void clear() {
    synchronized (lock) {
      this.persistence.close();
      this.persistence.reset();
      this.persistence.open();
      this.knownPeers.clear();
    }
  }
}
