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

import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.networks.Addressing;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.DeserializeException;
import java.time.Duration;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public record P2PConfig(
    NetworkConfig networkConfig,
    ChannelConfig channelConfig,
    PeerDiscoveryConfig peerDiscoveryConfig,
    PeerLivenessConfig peerLivenessConfig,
    ProxyConfig proxyConfig,
    boolean usePeerAllowList,
    ImmutableSet<NodeId> peerAllowList) {

  private static final Logger log = LogManager.getLogger();

  public record ChannelConfig(
      boolean useProxyProtocol,
      int peerConnectionTimeout,
      int maxInboundChannels,
      int maxOutboundChannels,
      int channelBufferSize) {
    public static ChannelConfig fromRuntimeProperties(RuntimeProperties properties) {
      return new ChannelConfig(
          properties.get("network.p2p.use_proxy_protocol", false),
          properties.get("network.p2p.peer_connection_timeout", 5000),
          properties.get("network.p2p.max_inbound_channels", 1024),
          properties.get("network.p2p.max_outbound_channels", 1024),
          properties.get("network.p2p.channel_buffer_size", 255));
    }
  }

  public record NetworkConfig(String listenAddress, int listenPort, int broadcastPort) {
    public static NetworkConfig fromRuntimeProperties(RuntimeProperties properties) {
      final var listenPort = properties.get("network.p2p.listen_port", 30000);
      return new NetworkConfig(
          properties.get("network.p2p.listen_address", "0.0.0.0"),
          listenPort,
          properties.get("network.p2p.broadcast_port", listenPort));
    }
  }

  public record PeerDiscoveryConfig(
      ImmutableList<String> seedNodes,
      int defaultPort,
      long discoveryInterval,
      ImmutableSet<NodeId> privatePeers) {
    public static PeerDiscoveryConfig fromRuntimeProperties(
        Addressing addressing, RuntimeProperties properties) {
      final var seedNodes =
          Arrays.stream(properties.get("network.p2p.seed_nodes", "").split(","))
              .map(String::trim)
              .filter(hn -> !hn.isEmpty())
              .collect(ImmutableList.toImmutableList());

      final var privatePeers =
          Arrays.stream(properties.get("network.p2p.private_peers", "").split(","))
              .filter(not(String::isEmpty))
              .map(n -> P2PConfig.parseNodeId(addressing, n))
              .collect(ImmutableSet.toImmutableSet());

      return new PeerDiscoveryConfig(
          seedNodes,
          properties.get("network.p2p.default_port", 30000),
          properties.get("network.p2p.discovery_interval", 30_000),
          privatePeers);
    }
  }

  public record PeerLivenessConfig(long peerLivenessCheckInterval, long pingTimeout) {
    public static PeerLivenessConfig fromRuntimeProperties(RuntimeProperties properties) {
      return new PeerLivenessConfig(
          properties.get("network.p2p.peer_liveness_check_interval", 10000),
          properties.get("network.p2p.ping_timeout", 5000));
    }
  }

  public record ProxyConfig(
      boolean proxyEnabled,
      boolean useProxies,
      ImmutableSet<NodeId> authorizedProxies,
      ImmutableSet<NodeId> authorizedProxiedPeers,
      Duration issuedProxyCertificateValidityDuration,
      boolean guardEnabled) {
    public static ProxyConfig fromRuntimeProperties(
        Addressing addressing, RuntimeProperties properties) {
      final var authorizedProxies =
          Arrays.stream(properties.get("network.p2p.proxy.authorized_proxies", "").split(","))
              .filter(not(String::isEmpty))
              .map(n -> P2PConfig.parseNodeId(addressing, n))
              .collect(ImmutableSet.toImmutableSet());

      final var authorizedProxiedNodes =
          Arrays.stream(properties.get("network.p2p.proxy.authorized_proxied_nodes", "").split(","))
              .filter(not(String::isEmpty))
              .map(n -> P2PConfig.parseNodeId(addressing, n))
              .collect(ImmutableSet.toImmutableSet());

      final var config =
          new ProxyConfig(
              properties.get("network.p2p.proxy.enabled", false),
              properties.get("network.p2p.proxy.use_proxies", false),
              authorizedProxies,
              authorizedProxiedNodes,
              Duration.ofMillis(
                  properties.get(
                      "network.p2p.proxy.issued_certificate_validity_duration_ms", 3600000)),
              properties.get("network.p2p.proxy.guard.enabled", false));

      if (config.useProxies() && config.authorizedProxies().isEmpty()) {
        throw new IllegalArgumentException(
            "authorizedProxies can't be empty if useProxies is true");
      }

      if (!config.useProxies() && !config.authorizedProxies().isEmpty()) {
        log.warn("authorizedProxies config will be ignored because useProxies is false");
      }

      return config;
    }
  }

  public static P2PConfig fromRuntimeProperties(
      Addressing addressing, RuntimeProperties properties) {

    final var peerAllowList =
        Arrays.stream(properties.get("network.p2p.peer_allow_list", "").split(","))
            .filter(not(String::isEmpty))
            .map(n -> P2PConfig.parseNodeId(addressing, n))
            .collect(ImmutableSet.toImmutableSet());

    final var config =
        new P2PConfig(
            NetworkConfig.fromRuntimeProperties(properties),
            ChannelConfig.fromRuntimeProperties(properties),
            PeerDiscoveryConfig.fromRuntimeProperties(addressing, properties),
            PeerLivenessConfig.fromRuntimeProperties(properties),
            ProxyConfig.fromRuntimeProperties(addressing, properties),
            properties.get("network.p2p.use_peer_allow_list", false),
            peerAllowList);

    if (config.usePeerAllowList() && config.peerAllowList().isEmpty()) {
      throw new IllegalArgumentException("peerAllowList can't be empty if usePeerAllowList is set");
    }

    if (!config.usePeerAllowList() && !config.peerAllowList().isEmpty()) {
      log.warn(
          "peerAllowList is non empty but will be ignored because usePeerAllowList is not set");
    }

    return config;
  }

  private static NodeId parseNodeId(Addressing addressing, String s) {
    try {
      return NodeId.fromPublicKey(addressing.forNodes().parse(s));
    } catch (DeserializeException e) {
      throw new IllegalArgumentException("Can't parse node ID from " + s, e);
    }
  }
}
