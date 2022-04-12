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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.RadixNodeUri;
import java.util.Set;
import org.junit.Test;

public final class AddressBookTest {

  @Test
  public void address_book_should_filter_out_peers_with_different_network_hrp() {
    final var self =
        RadixNodeUri.fromPubKeyAndAddress(
            1, ECKeyPair.generateNew().getPublicKey(), "1.1.1.1", 30000);
    final var invalidPeer =
        RadixNodeUri.fromPubKeyAndAddress(
            2, ECKeyPair.generateNew().getPublicKey(), "2.2.2.2", 30000);

    final var persistence = new InMemoryAddressBookPersistence();
    persistence.saveEntry(AddressBookEntry.create(invalidPeer)); // insert directly into storage

    final var sut =
        new AddressBook(self, mock(P2PConfig.class), rmock(EventDispatcher.class), persistence);
    assertTrue(sut.knownPeers().isEmpty()); // invalid peer should be filtered out at init
    assertTrue(sut.findById(invalidPeer.getNodeId()).isEmpty());

    sut.addUncheckedPeers(Set.of(invalidPeer)); // add after initial cleanup
    assertTrue(sut.knownPeers().isEmpty()); // should also be filtered out
  }

  @Test
  public void address_book_should_sort_entries_by_latest_connection_status() {
    final var self =
        RadixNodeUri.fromPubKeyAndAddress(
            1, ECKeyPair.generateNew().getPublicKey(), "127.0.0.10", 30303);
    final var peerKey = ECKeyPair.generateNew().getPublicKey();
    final var peerId = NodeId.fromPublicKey(peerKey);
    final var addr1 = RadixNodeUri.fromPubKeyAndAddress(1, peerKey, "127.0.0.1", 30303);
    final var addr2 = RadixNodeUri.fromPubKeyAndAddress(1, peerKey, "127.0.0.2", 30303);
    final var addr3 = RadixNodeUri.fromPubKeyAndAddress(1, peerKey, "127.0.0.3", 30303);
    final var addr4 = RadixNodeUri.fromPubKeyAndAddress(1, peerKey, "127.0.0.4", 30303);

    final var sut =
        new AddressBook(
            self,
            mock(P2PConfig.class),
            rmock(EventDispatcher.class),
            new InMemoryAddressBookPersistence());

    sut.addUncheckedPeers(ImmutableSet.of(addr1, addr2, addr3, addr4));

    sut.addOrUpdatePeerWithSuccessfulConnection(addr1);
    final var bestAddr = sut.bestKnownAddressesById(peerId).get(0);
    assertEquals(addr1, bestAddr);

    sut.addOrUpdatePeerWithSuccessfulConnection(addr2);
    final var bestAddr2 = sut.bestKnownAddressesById(peerId).get(0);
    assertTrue(bestAddr2 == addr1 || bestAddr2 == addr2);

    sut.addOrUpdatePeerWithFailedConnection(addr1);
    final var bestAddr3 = sut.bestKnownAddressesById(peerId).get(0);
    assertEquals(addr2, bestAddr3);

    sut.addOrUpdatePeerWithFailedConnection(addr2);
    final var bestAddr4 = sut.bestKnownAddressesById(peerId).get(0);
    assertTrue(bestAddr4 == addr3 || bestAddr4 == addr4);

    sut.addOrUpdatePeerWithSuccessfulConnection(addr4);
    final var bestAddr5 = sut.bestKnownAddressesById(peerId).get(0);
    assertEquals(addr4, bestAddr5);
  }

  @Test
  public void address_book_should_cycle_failed_connection_uris() {
    final var self =
        RadixNodeUri.fromPubKeyAndAddress(
            1, ECKeyPair.generateNew().getPublicKey(), "127.0.0.10", 30303);
    final var peerKey = ECKeyPair.generateNew().getPublicKey();
    final var peerId = NodeId.fromPublicKey(peerKey);
    final var addr1 = RadixNodeUri.fromPubKeyAndAddress(1, peerKey, "127.0.0.1", 30303);
    final var addr2 = RadixNodeUri.fromPubKeyAndAddress(1, peerKey, "127.0.0.2", 30303);
    final var addr3 = RadixNodeUri.fromPubKeyAndAddress(1, peerKey, "127.0.0.3", 30303);

    final var sut =
        new AddressBook(
            self,
            mock(P2PConfig.class),
            rmock(EventDispatcher.class),
            new InMemoryAddressBookPersistence());

    sut.addUncheckedPeers(ImmutableSet.of(addr1, addr2, addr3));

    var prevPrevBestAddr = sut.bestKnownAddressesById(peerId).get(0);
    sut.addOrUpdatePeerWithFailedConnection(prevPrevBestAddr);
    var prevBestAddr = sut.bestKnownAddressesById(peerId).get(0);
    for (int i = 0; i < 50; i++) {
      sut.addOrUpdatePeerWithFailedConnection(prevBestAddr);
      final var currBestAddr = sut.bestKnownAddressesById(peerId).get(0);
      assertNotEquals(prevBestAddr, currBestAddr);
      assertNotEquals(prevPrevBestAddr, prevBestAddr);
      prevPrevBestAddr = prevBestAddr;
      prevBestAddr = currBestAddr;
    }
  }

  @Test
  public void unchecked_localhost_uri_should_not_be_added() {
    final var self =
        RadixNodeUri.fromPubKeyAndAddress(
            1, ECKeyPair.generateNew().getPublicKey(), "192.168.50.50", 30303);
    final var localAddrSamePort =
        RadixNodeUri.fromPubKeyAndAddress(
            1, ECKeyPair.generateNew().getPublicKey(), "127.0.0.1", 30303);
    final var publicAddrSamePort =
        RadixNodeUri.fromPubKeyAndAddress(
            1, ECKeyPair.generateNew().getPublicKey(), self.getHost(), 30303);
    final var localAddrDifferentPort =
        RadixNodeUri.fromPubKeyAndAddress(
            1, ECKeyPair.generateNew().getPublicKey(), "127.0.0.1", 30304);

    final var persistence = new InMemoryAddressBookPersistence();
    final var p2pConfig = mock(P2PConfig.class);
    when(p2pConfig.broadcastPort()).thenReturn(30303);
    when(p2pConfig.listenPort()).thenReturn(30303);
    final var sut = new AddressBook(self, p2pConfig, rmock(EventDispatcher.class), persistence);

    // Self addresses with the same port shouldn't be added
    sut.addUncheckedPeers(ImmutableSet.of(localAddrSamePort, publicAddrSamePort));
    assertTrue(persistence.getAllEntries().isEmpty());

    // Self address with a different port should be added
    sut.addUncheckedPeers(ImmutableSet.of(localAddrDifferentPort));
    assertEquals(1, persistence.getAllEntries().size());
  }
}
