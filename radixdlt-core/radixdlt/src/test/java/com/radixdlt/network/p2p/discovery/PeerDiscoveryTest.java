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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.network.p2p.discovery.util.SeedNodesConfigParser;
import com.radixdlt.network.p2p.test.DeterministicP2PNetworkTest;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;

public final class PeerDiscoveryTest extends DeterministicP2PNetworkTest {

  @Test
  public void when_discover_peers_then_should_connect_to_some_peers() throws Exception {
    setupTestRunner(5, defaultProperties());

    // add 4 peers to the addr book
    testNetworkRunner
        .addressBook(0)
        .addUncheckedPeers(
            Set.of(
                testNetworkRunner.getUri(1),
                testNetworkRunner.getUri(2),
                testNetworkRunner.getUri(3),
                testNetworkRunner.getUri(4)));

    testNetworkRunner
        .getInstance(0, new Key<EventDispatcher<DiscoverPeers>>() {})
        .dispatch(DiscoverPeers.create());

    processForCount(10);

    // with 10 slots (default), max num of peers to connect is 3 (10/2 - 2)
    assertEquals(3L, testNetworkRunner.peerManager(0).activeChannels().size());
  }

  @Test
  public void when_unexpected_response_then_ban_peer() throws Exception {
    setupTestRunner(1, defaultProperties());

    final var unexpectedSender = BFTNode.random();
    final var peersResponse =
        PeersResponse.create(
            ImmutableSet.of(
                RadixNodeUri.fromPubKeyAndAddress(
                    0, ECKeyPair.generateNew().getPublicKey(), "127.0.0.1", 1234)));

    testNetworkRunner
        .getInstance(0, PeerDiscovery.class)
        .peersResponseRemoteEventProcessor()
        .process(unexpectedSender, peersResponse);

    processAll();

    assertTrue(
        testNetworkRunner
            .addressBook(0)
            .findById(NodeId.fromPublicKey(unexpectedSender.getKey()))
            .get()
            .isBanned());
  }

  @Test
  public void when_get_peers_then_must_not_return_private_peers() {
    final var selfKey = ECKeyPair.generateNew();
    final var selfUri =
        RadixNodeUri.fromPubKeyAndAddress(1, selfKey.getPublicKey(), "127.0.0.1", 3000);

    final var config = mock(P2PConfig.PeerDiscoveryConfig.class);
    final var addressBook = mock(AddressBook.class);
    final RemoteEventDispatcher<PeersResponse> peersResponseDispatcher =
        rmock(RemoteEventDispatcher.class);

    final var sut =
        new PeerDiscovery(
            selfUri,
            config,
            mock(PeerManager.class),
            addressBook,
            mock(PeerControl.class),
            mock(SeedNodesConfigParser.class),
            rmock(RemoteEventDispatcher.class),
            peersResponseDispatcher);

    final var peer1 = randomNodeUri();
    final var peer2 = randomNodeUri();
    final var peer3 = randomNodeUri();
    final var peer4 = randomNodeUri();
    final var peer5 = randomNodeUri();
    final var peer6 = randomNodeUri();

    // peer3 is included in the address book
    when(addressBook.bestCandidatesToConnect())
        .thenReturn(Stream.of(peer1, peer2, peer3, peer4, peer5, peer6));

    // peer3 shouldn't be returned in discovery response
    when(config.privatePeers()).thenReturn(ImmutableSet.of(peer3.getNodeId()));

    sut.getPeersRemoteEventProcessor().process(BFTNode.random(), GetPeers.create());

    verify(peersResponseDispatcher, times(1))
        .dispatch(
            (BFTNode) any(),
            argThat(
                response ->
                    response.getPeers().containsAll(Set.of(peer1, peer2, peer4, peer5, peer6))
                        && !response.getPeers().contains(peer3)));
  }

  private RadixNodeUri randomNodeUri() {
    return RadixNodeUri.fromPubKeyAndAddress(
        0, ECKeyPair.generateNew().getPublicKey(), "127.0.0.1", 1);
  }
}
