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

package com.radixdlt.network.p2p.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.inject.Key;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.serialization.Serialization;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import java.security.SecureRandom;
import java.util.Optional;

final class MockP2PNetwork {
  private ImmutableList<TestNode> nodes;

  // this needs to be mutable due to circular dependency in runner
  void setNodes(ImmutableList<TestNode> nodes) {
    this.nodes = nodes;
  }

  void createChannel(int clientPeerIndex, RadixNodeUri serverPeerUri) {
    final var clientPeer = nodes.get(clientPeerIndex);
    final var serverPeerOpt =
        nodes.stream()
            .filter(
                p ->
                    p.uri.getHost().equals(serverPeerUri.getHost())
                        && p.uri.getPort() == serverPeerUri.getPort())
            .findAny();

    final var clientSocketChannel = mock(SocketChannel.class);

    final var clientChannel =
        new PeerChannel(
            clientPeer.injector.getInstance(P2PConfig.class),
            Addressing.ofNetwork(Network.LOCALNET),
            1,
            "fork1",
            clientPeer.injector.getInstance(SystemCounters.class),
            clientPeer.injector.getInstance(Serialization.class),
            new SecureRandom(),
            ECKeyOps.fromKeyPair(clientPeer.keyPair),
            clientPeer.injector.getInstance(new Key<EventDispatcher<PeerEvent>>() {}),
            Optional.of(serverPeerUri),
            clientSocketChannel,
            Optional.empty());

    if (serverPeerOpt.isEmpty()) {
      clientChannel.channelActive(null /* unused */);
      clientChannel.channelInactive(null /* unused */);
      return;
    }

    final var serverPeer = serverPeerOpt.get();

    final var serverSocketChannel = mock(SocketChannel.class);

    final var serverChannel =
        new PeerChannel(
            serverPeer.injector.getInstance(P2PConfig.class),
            Addressing.ofNetwork(Network.LOCALNET),
            1,
            "fork1",
            serverPeer.injector.getInstance(SystemCounters.class),
            serverPeer.injector.getInstance(Serialization.class),
            new SecureRandom(),
            ECKeyOps.fromKeyPair(serverPeer.keyPair),
            serverPeer.injector.getInstance(new Key<EventDispatcher<PeerEvent>>() {}),
            Optional.empty(),
            serverSocketChannel,
            Optional.empty());

    when(clientSocketChannel.writeAndFlush(any()))
        .thenAnswer(
            inv -> {
              final var rawData = inv.getArgument(0);
              serverChannel.channelRead0(null, (ByteBuf) rawData);
              return null;
            });

    when(serverSocketChannel.writeAndFlush(any()))
        .thenAnswer(
            inv -> {
              final var rawData = inv.getArgument(0);
              clientChannel.channelRead0(null, (ByteBuf) rawData);
              return null;
            });

    when(clientSocketChannel.close())
        .thenAnswer(
            inv -> {
              final var mockChannel = mock(ChannelHandlerContext.class);
              when(mockChannel.channel()).thenReturn(mock(Channel.class));
              clientChannel.channelInactive(mockChannel);
              serverChannel.channelInactive(mockChannel);
              return null;
            });

    when(serverSocketChannel.close())
        .thenAnswer(
            inv -> {
              final var mockChannel = mock(ChannelHandlerContext.class);
              when(mockChannel.channel()).thenReturn(mock(Channel.class));
              serverChannel.channelInactive(mockChannel);
              clientChannel.channelInactive(mockChannel);
              return null;
            });

    serverChannel.channelActive(null);
    clientChannel.channelActive(null);
  }
}
