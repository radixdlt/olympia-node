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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.radixdlt.network.p2p.test.DeterministicP2PNetworkTest;
import com.radixdlt.network.p2p.test.P2PTestNetworkRunner.TestCounters;
import com.radixdlt.networks.Network;
import java.time.Duration;
import java.util.Set;
import org.junit.After;
import org.junit.Test;

public final class PeerManagerTest extends DeterministicP2PNetworkTest {

  @After
  public void cleanup() {
    testNetworkRunner.cleanup();
  }

  @Test
  public void when_findOrCreateChannel_then_should_create_if_not_exists() throws Exception {
    setupTestRunner(3, defaultProperties());

    testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(uriOfNode(1)));
    final var channelFuture =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(1).getNodeId());

    processForCount(3);

    assertEquals(uriOfNode(1), channelFuture.get().getUri().get());

    assertEquals(1L, testNetworkRunner.peerManager(0).activeChannels().size());
    assertEquals(1L, testNetworkRunner.peerManager(1).activeChannels().size());
  }

  @Test
  public void should_disconnect_the_least_used_channels_when_over_limit() throws Exception {
    final var props = defaultProperties();
    props.set("network.p2p.max_outbound_channels", 3); // 3 outbound channels allowed
    setupTestRunner(5, props);

    testNetworkRunner
        .addressBook(0)
        .addUncheckedPeers(Set.of(uriOfNode(1), uriOfNode(2), uriOfNode(3), uriOfNode(4)));
    final var channel1Future =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(1).getNodeId());

    final var channel2Future =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(2).getNodeId());

    final var channel3Future =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(3).getNodeId());

    processAll();

    // two messages sent over node1 channel
    channel1Future.get().send(new byte[] {0x01});
    channel1Future.get().send(new byte[] {0x02});

    // one messages sent over node2 channel
    channel2Future.get().send(new byte[] {0x03});

    // three messages sent over node3 channel
    channel3Future.get().send(new byte[] {0x01});
    channel3Future.get().send(new byte[] {0x01});
    channel3Future.get().send(new byte[] {0x01});

    final var channel4Future =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(4).getNodeId());

    processAll();

    assertEquals(3L, testNetworkRunner.peerManager(0).activeChannels().size());
    assertEquals(1L, testNetworkRunner.peerManager(1).activeChannels().size());
    assertEquals(
        0L,
        testNetworkRunner.peerManager(2).activeChannels().size()); // node2 should be disconnected
    assertEquals(1L, testNetworkRunner.peerManager(3).activeChannels().size());
    assertEquals(1L, testNetworkRunner.peerManager(4).activeChannels().size());
  }

  @Test
  public void should_not_connect_to_banned_peers() throws Exception {
    final var props = defaultProperties();
    setupTestRunner(5, props);

    testNetworkRunner
        .addressBook(0)
        .addUncheckedPeers(Set.of(uriOfNode(1), uriOfNode(2), uriOfNode(3), uriOfNode(4)));
    testNetworkRunner.addressBook(1).addUncheckedPeers(Set.of(uriOfNode(0)));

    // ban node1 and node3 on node0
    testNetworkRunner
        .getInstance(0, PeerControl.class)
        .banPeer(uriOfNode(1).getNodeId(), Duration.ofHours(1), "");
    testNetworkRunner
        .getInstance(0, PeerControl.class)
        .banPeer(uriOfNode(3).getNodeId(), Duration.ofHours(1), "");

    // try outbound connection (to node3)
    final var channel1Future =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(3).getNodeId());

    processAll();

    assertTrue(channel1Future.isCompletedExceptionally());
    assertEquals(0L, testNetworkRunner.peerManager(0).activeChannels().size());
    assertEquals(0L, testNetworkRunner.peerManager(3).activeChannels().size());

    // try inbound connection (from node1)

    final var channel2Future =
        testNetworkRunner.peerManager(1).findOrCreateChannel(uriOfNode(0).getNodeId());

    processAll();

    assertEquals(0L, testNetworkRunner.peerManager(0).activeChannels().size());
    assertEquals(0L, testNetworkRunner.peerManager(1).activeChannels().size());
  }

  @Test
  public void should_disconnect_just_banned_peer() throws Exception {
    final var props = defaultProperties();
    setupTestRunner(2, props);

    testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(uriOfNode(1)));

    final var channel1Future =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(1).getNodeId());

    processAll();

    // assert the connections is successful
    assertTrue(channel1Future.isDone());
    assertEquals(1L, testNetworkRunner.peerManager(0).activeChannels().size());
    assertEquals(1L, testNetworkRunner.peerManager(1).activeChannels().size());

    // ban node0 on node1
    testNetworkRunner
        .getInstance(1, PeerControl.class)
        .banPeer(uriOfNode(0).getNodeId(), Duration.ofHours(1), "");

    processAll();

    // assert connection closed
    assertEquals(0L, testNetworkRunner.peerManager(0).activeChannels().size());
    assertEquals(0L, testNetworkRunner.peerManager(1).activeChannels().size());
  }

  @Test
  public void should_try_more_than_one_peer_address_to_connect() throws Exception {
    final var props = defaultProperties();
    setupTestRunner(2, props);

    final var validUri = uriOfNode(1);
    final var invalidPortOffset =
        20; /* just to be sure that it's not assigned to any other peer in the test network */
    final var invalidUri1 = copyWithPortOffset(validUri, invalidPortOffset);
    final var invalidUri2 = copyWithPortOffset(validUri, invalidPortOffset + 1);
    final var invalidUri3 = copyWithPortOffset(validUri, invalidPortOffset + 2);

    testNetworkRunner
        .addressBook(0)
        .addUncheckedPeers(Set.of(invalidUri1, invalidUri2, invalidUri3));

    final var channelFuture1 =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(1).getNodeId());
    processAll();

    /* the connection failed and 3 attempts were made (3 URIs tried) */
    assertTrue(channelFuture1.isCompletedExceptionally());
    assertEquals(
        3, testNetworkRunner.getInstance(0, TestCounters.class).outboundChannelsBootstrapped);

    /* try same thing again */
    final var channelFuture2 =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(1).getNodeId());
    processAll();

    /* still failing with 3 more attempts (no URIs got blacklisted) */
    assertTrue(channelFuture2.isCompletedExceptionally());
    assertEquals(
        6, testNetworkRunner.getInstance(0, TestCounters.class).outboundChannelsBootstrapped);

    /* add some more invalid URIs and a valid one */
    final var invalidUri4 = copyWithPortOffset(validUri, invalidPortOffset + 3);
    final var invalidUri5 = copyWithPortOffset(validUri, invalidPortOffset + 4);
    final var invalidUri6 = copyWithPortOffset(validUri, invalidPortOffset + 5);

    testNetworkRunner
        .addressBook(0)
        .addUncheckedPeers(Set.of(invalidUri4, invalidUri5, invalidUri6, validUri));

    final var channelFuture3 =
        testNetworkRunner.peerManager(0).findOrCreateChannel(uriOfNode(1).getNodeId());
    processAll();

    /* the connection succeeds and between 1-4 new connect attempts were made */
    assertTrue(channelFuture3.isDone());
    assertFalse(channelFuture3.isCompletedExceptionally());
    final var channelsCounterAtTheEnd =
        testNetworkRunner.getInstance(0, TestCounters.class).outboundChannelsBootstrapped;
    assertTrue(channelsCounterAtTheEnd >= 7 && channelsCounterAtTheEnd <= 10);
  }

  private RadixNodeUri copyWithPortOffset(RadixNodeUri base, int portOffset) {
    return RadixNodeUri.fromPubKeyAndAddress(
        Network.MAINNET.getId(),
        base.getNodeId().getPublicKey(),
        base.getHost(),
        base.getPort() + portOffset);
  }
}
