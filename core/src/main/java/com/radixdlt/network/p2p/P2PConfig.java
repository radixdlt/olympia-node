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

import com.google.common.collect.ImmutableList;
import com.radixdlt.utils.properties.RuntimeProperties;
import java.util.Arrays;

/** Static configuration data for P2P layer. */
public interface P2PConfig {
  /** List of seed nodes for discovery. */
  ImmutableList<String> seedNodes();

  /** Default port to use for discovery seed nodes. */
  int defaultPort();

  /** An interval at which peer discovery rounds trigger. */
  long discoveryInterval();

  /** Get the host to bind the p2p server to. */
  String listenAddress();

  /** Get the port number to bind the p2p server to. */
  int listenPort();

  /** Specifies whether the server should process the PROXY header for inbound connections. */
  boolean useProxyProtocol();

  /** Get node's port number to broadcast to other peers. */
  int broadcastPort();

  /** The timeout for initiating outbound peer connection. */
  int peerConnectionTimeout();

  /**
   * Get the maximum number of inbound open channels allowed. Note that each channel consumes some
   * resources on the host machine, and there may be other global operating-system defined limits
   * that come into play.
   */
  int maxInboundChannels();

  /**
   * Get the maximum number of outbound open channels allowed. Note that each channel consumes some
   * resources on the host machine, and there may be other global operating-system defined limits
   * that come into play.
   */
  int maxOutboundChannels();

  /**
   * Get the buffer size of incoming messages for each TCP connection.
   *
   * @return the size of a message buffer
   */
  int channelBufferSize();

  /** An interval at which peer liveness check is triggered (ping message). */
  long peerLivenessCheckInterval();

  /**
   * The timeout for receiving a Pong message. PeerLivenessLost event is triggered if pong is not
   * received on time.
   */
  long pingTimeout();

  /**
   * Create a configuration from specified {@link RuntimeProperties}.
   *
   * @param properties the properties to read the configuration from
   * @return The configuration
   */
  static P2PConfig fromRuntimeProperties(RuntimeProperties properties) {
    return new P2PConfig() {
      @Override
      public ImmutableList<String> seedNodes() {
        return Arrays.stream(properties.get("network.p2p.seed_nodes", "").split(","))
            .map(String::trim)
            .filter(hn -> !hn.isEmpty())
            .collect(ImmutableList.toImmutableList());
      }

      @Override
      public int defaultPort() {
        return properties.get("network.p2p.default_port", 30000);
      }

      @Override
      public long discoveryInterval() {
        return properties.get("network.p2p.discovery_interval", 30_000);
      }

      @Override
      public String listenAddress() {
        return properties.get("network.p2p.listen_address", "0.0.0.0");
      }

      @Override
      public int listenPort() {
        return properties.get("network.p2p.listen_port", 30000);
      }

      @Override
      public boolean useProxyProtocol() {
        return properties.get("network.p2p.use_proxy_protocol", false);
      }

      @Override
      public int broadcastPort() {
        return properties.get("network.p2p.broadcast_port", listenPort());
      }

      @Override
      public int peerConnectionTimeout() {
        return properties.get("network.p2p.peer_connection_timeout", 5000);
      }

      @Override
      public int maxInboundChannels() {
        return properties.get("network.p2p.max_inbound_channels", 1024);
      }

      @Override
      public int maxOutboundChannels() {
        return properties.get("network.p2p.max_outbound_channels", 1024);
      }

      @Override
      public int channelBufferSize() {
        return properties.get("network.p2p.channel_buffer_size", 255);
      }

      @Override
      public long peerLivenessCheckInterval() {
        return properties.get("network.p2p.peer_liveness_check_interval", 10000);
      }

      @Override
      public long pingTimeout() {
        return properties.get("network.p2p.ping_timeout", 5000);
      }
    };
  }
}
