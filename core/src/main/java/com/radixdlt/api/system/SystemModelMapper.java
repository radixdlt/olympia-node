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

package com.radixdlt.api.system;

import static com.radixdlt.monitoring.SystemCounters.CounterType.*;

import com.google.inject.Inject;
import com.radixdlt.api.system.openapitools.model.Address;
import com.radixdlt.api.system.openapitools.model.AddressBookEntry;
import com.radixdlt.api.system.openapitools.model.BFTMetrics;
import com.radixdlt.api.system.openapitools.model.BFTPacemakerMetrics;
import com.radixdlt.api.system.openapitools.model.BFTSyncMetrics;
import com.radixdlt.api.system.openapitools.model.BFTVertexStoreMetrics;
import com.radixdlt.api.system.openapitools.model.MempoolMetrics;
import com.radixdlt.api.system.openapitools.model.NetworkingConfiguration;
import com.radixdlt.api.system.openapitools.model.NetworkingInboundMetrics;
import com.radixdlt.api.system.openapitools.model.NetworkingMetrics;
import com.radixdlt.api.system.openapitools.model.NetworkingOutboundMetrics;
import com.radixdlt.api.system.openapitools.model.Peer;
import com.radixdlt.api.system.openapitools.model.PeerChannel;
import com.radixdlt.api.system.openapitools.model.SyncConfiguration;
import com.radixdlt.api.system.openapitools.model.SyncMetrics;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry.PeerAddressEntry;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry.PeerAddressEntry.LatestConnectionStatus;
import com.radixdlt.networks.Addressing;
import com.radixdlt.sync.SyncConfig;
import java.math.BigDecimal;
import java.time.Instant;

public final class SystemModelMapper {
  private final Addressing addressing;

  @Inject
  SystemModelMapper(Addressing addressing) {
    this.addressing = addressing;
  }

  public NetworkingConfiguration networkingConfiguration(ECPublicKey self, P2PConfig config) {
    return new NetworkingConfiguration()
        .defaultPort(config.defaultPort())
        .discoveryInterval(config.discoveryInterval())
        .listenAddress(config.listenAddress())
        .listenPort(config.listenPort())
        .broadcastPort(config.broadcastPort())
        .peerConnectionTimeout(config.peerConnectionTimeout())
        .maxInboundChannels(config.maxInboundChannels())
        .maxOutboundChannels(config.maxOutboundChannels())
        .channelBufferSize(config.channelBufferSize())
        .peerLivenessCheckInterval(config.peerLivenessCheckInterval())
        .pingTimeout(config.pingTimeout())
        .seedNodes(config.seedNodes())
        .nodeAddress(addressing.forNodes().of(self));
  }

  public SyncConfiguration syncConfiguration(SyncConfig syncConfig) {
    return new SyncConfiguration()
        .syncCheckInterval(syncConfig.syncCheckInterval())
        .syncCheckMaxPeers(syncConfig.syncCheckMaxPeers())
        .requestTimeout(syncConfig.syncRequestTimeout())
        .ledgerStatusUpdateMaxPeersToNotify(syncConfig.ledgerStatusUpdateMaxPeersToNotify())
        .maxLedgerUpdatesRate(BigDecimal.valueOf(syncConfig.maxLedgerUpdatesRate()));
  }

  public NetworkingMetrics networkingMetrics(SystemCounters counters) {
    return new NetworkingMetrics()
        .bytesReceived(counters.get(NETWORKING_BYTES_RECEIVED))
        .bytesSent(counters.get(NETWORKING_BYTES_SENT))
        .inbound(networkingInboundMetrics(counters))
        .outbound(networkingOutboundMetrics(counters));
  }

  public NetworkingInboundMetrics networkingInboundMetrics(SystemCounters counters) {
    return new NetworkingInboundMetrics()
        .discarded(counters.get(MESSAGES_INBOUND_DISCARDED))
        .processed(counters.get(MESSAGES_INBOUND_PROCESSED))
        .received(counters.get(MESSAGES_INBOUND_RECEIVED));
  }

  public NetworkingOutboundMetrics networkingOutboundMetrics(SystemCounters counters) {
    return new NetworkingOutboundMetrics()
        .aborted(counters.get(MESSAGES_OUTBOUND_ABORTED))
        .processed(counters.get(MESSAGES_OUTBOUND_PROCESSED))
        .pending(counters.get(MESSAGES_OUTBOUND_PENDING))
        .aborted(counters.get(MESSAGES_OUTBOUND_ABORTED))
        .sent(counters.get(MESSAGES_OUTBOUND_SENT));
  }

  public MempoolMetrics mempoolMetrics(SystemCounters counters) {
    return new MempoolMetrics()
        .addFailure(counters.get(MEMPOOL_ADD_FAILURE))
        .addSuccess(counters.get(MEMPOOL_ADD_SUCCESS))
        .currentSize(counters.get(MEMPOOL_CURRENT_SIZE))
        .relaysSent(counters.get(MEMPOOL_RELAYS_SENT));
  }

  public BFTMetrics bftMetrics(SystemCounters counters) {
    return new BFTMetrics()
        .committedVertices(counters.get(BFT_COMMITTED_VERTICES))
        .eventsReceived(counters.get(BFT_EVENTS_RECEIVED))
        .noVotesSent(counters.get(BFT_NO_VOTES_SENT))
        .timeoutQuorums(counters.get(BFT_TIMEOUT_QUORUMS))
        .voteQuorums(counters.get(BFT_VOTE_QUORUMS))
        .sync(bftSyncMetrics(counters))
        .pacemaker(bftPacemakerMetrics(counters))
        .vertexStore(bftVertexStoreMetrics(counters));
  }

  public SyncMetrics syncMetrics(SystemCounters counters) {
    return new SyncMetrics()
        .currentStateVersion(counters.get(SYNC_CURRENT_STATE_VERSION))
        .targetStateVersion(counters.get(SYNC_TARGET_STATE_VERSION))
        .invalidResponsesReceived(counters.get(SYNC_INVALID_RESPONSES_RECEIVED))
        .validResponsesReceived(counters.get(SYNC_VALID_RESPONSES_RECEIVED))
        .remoteRequestsReceived(counters.get(SYNC_REMOTE_REQUESTS_RECEIVED));
  }

  public BFTVertexStoreMetrics bftVertexStoreMetrics(SystemCounters counters) {
    return new BFTVertexStoreMetrics()
        .forks(counters.get(BFT_VERTEX_STORE_FORKS))
        .indirectParents(counters.get(BFT_VERTEX_STORE_INDIRECT_PARENTS))
        .rebuilds(counters.get(BFT_VERTEX_STORE_REBUILDS))
        .size(counters.get(BFT_VERTEX_STORE_SIZE));
  }

  public BFTPacemakerMetrics bftPacemakerMetrics(SystemCounters counters) {
    return new BFTPacemakerMetrics()
        .proposalsSent(counters.get(BFT_PACEMAKER_PROPOSALS_SENT))
        .proposedTransactions(counters.get(BFT_PACEMAKER_PROPOSED_TRANSACTIONS))
        .round(counters.get(BFT_PACEMAKER_ROUND))
        .timedOutRounds(counters.get(BFT_PACEMAKER_TIMED_OUT_ROUNDS))
        .timeoutsSent(counters.get(BFT_PACEMAKER_TIMEOUTS_SENT));
  }

  public BFTSyncMetrics bftSyncMetrics(SystemCounters counters) {
    return new BFTSyncMetrics()
        .requestsReceived(counters.get(BFT_SYNC_REQUESTS_RECEIVED))
        .requestsSent(counters.get(BFT_SYNC_REQUESTS_SENT))
        .requestTimeouts(counters.get(BFT_SYNC_REQUEST_TIMEOUTS));
  }

  public Peer peer(PeersView.PeerInfo peerInfo) {
    var peerId = addressing.forNodes().of(peerInfo.getNodeId().getPublicKey());
    var peer = new Peer().peerId(peerId);

    peerInfo
        .getChannels()
        .forEach(
            channel -> {
              var peerChannel =
                  new PeerChannel()
                      .type(
                          channel.isOutbound() ? PeerChannel.TypeEnum.OUT : PeerChannel.TypeEnum.IN)
                      .localPort(channel.getPort())
                      .ip(channel.getHost());
              channel.getUri().map(RadixNodeUri::toString).ifPresent(peerChannel::uri);
              peer.addChannelsItem(peerChannel);
            });
    return peer;
  }

  public Address address(PeerAddressEntry entry) {
    return new Address()
        .uri(entry.getUri().toString())
        .blacklisted(entry.blacklisted())
        .lastConnectionStatus(
            Address.LastConnectionStatusEnum.fromValue(
                entry
                    .getLatestConnectionStatus()
                    .map(LatestConnectionStatus::toString)
                    .orElse("UNKNOWN")));
  }

  public AddressBookEntry addressBookEntry(
      com.radixdlt.network.p2p.addressbook.AddressBookEntry entry) {
    var addressBookEntry =
        new AddressBookEntry()
            .peerId(addressing.forNodes().of(entry.getNodeId().getPublicKey()))
            .banned(entry.isBanned());
    entry.bannedUntil().map(Instant::toEpochMilli).ifPresent(addressBookEntry::bannedUntil);
    entry.getKnownAddresses().stream()
        .map(this::address)
        .forEach(addressBookEntry::addKnownAddressesItem);

    return addressBookEntry;
  }
}
