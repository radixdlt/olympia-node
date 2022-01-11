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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.proxy.ProxyCertificate;
import com.radixdlt.network.p2p.proxy.ProxyCertificateData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class AddressBookTest {

  @Test
  public void address_book_should_filter_out_peers_with_different_network_hrp() {
    final var self = createNodeUri(1, "1.1.1.1");
    final var invalidPeer = createNodeUri(2, "2.2.2.2");

    final var persistence = new InMemoryAddressBookPersistence();

    persistence.saveEntry(AddressBookEntry.create(invalidPeer)); // insert directly into storage

    final var clock = createClock();
    final var sut = new AddressBook(self, rmock(EventDispatcher.class), persistence, clock);

    assertTrue(sut.knownPeers().isEmpty()); // invalid peer should be filtered out at init
    assertTrue(sut.findById(invalidPeer.getNodeId()).isEmpty());

    sut.addUncheckedPeers(Set.of(invalidPeer)); // add after initial cleanup
    assertTrue(sut.knownPeers().isEmpty()); // should also be filtered out
  }

  @Test
  public void address_book_should_sort_entries_by_latest_connection_status() {
    final var peerKey = ECKeyPair.generateNew().getPublicKey();
    final var peerId = NodeId.fromPublicKey(peerKey);
    final var self = createNodeUri(1, "127.0.0.10");
    final var addr1 = createNodeUri(1, peerKey, "127.0.0.1");
    final var addr2 = createNodeUri(1, peerKey, "127.0.0.2");
    final var addr3 = createNodeUri(1, peerKey, "127.0.0.3");
    final var addr4 = createNodeUri(1, peerKey, "127.0.0.4");

    final var sut = createAddressBook(self);

    sut.addUncheckedPeers(ImmutableSet.of(addr1, addr2, addr3, addr4));

    sut.addOrUpdatePeerWithSuccessfulConnection(addr1);
    final var bestAddr = sut.findBestKnownAddressById(peerId).orElseThrow();
    assertEquals(addr1, bestAddr);

    sut.addOrUpdatePeerWithSuccessfulConnection(addr2);
    final var bestAddr2 = sut.findBestKnownAddressById(peerId).orElseThrow();
    assertTrue(bestAddr2 == addr1 || bestAddr2 == addr2);

    sut.addOrUpdatePeerWithFailedConnection(addr1);
    final var bestAddr3 = sut.findBestKnownAddressById(peerId).orElseThrow();
    assertEquals(addr2, bestAddr3);

    sut.addOrUpdatePeerWithFailedConnection(addr2);
    final var bestAddr4 = sut.findBestKnownAddressById(peerId).orElseThrow();
    assertTrue(bestAddr4 == addr3 || bestAddr4 == addr4);

    sut.addOrUpdatePeerWithSuccessfulConnection(addr4);
    final var bestAddr5 = sut.findBestKnownAddressById(peerId).orElseThrow();
    assertTrue(bestAddr5 == addr3 || bestAddr5 == addr4);
  }

  @Test
  public void address_book_should_not_show_failed_connection_uris_while_they_postponed() {
    final var peerKey = ECKeyPair.generateNew().getPublicKey();
    final var peerId = NodeId.fromPublicKey(peerKey);

    final var self = createNodeUri(1, "127.0.0.10");
    final var addr1 = createNodeUri(1, peerKey, "127.0.0.1");
    final var addr2 = createNodeUri(1, peerKey, "127.0.0.2");
    final var addr3 = createNodeUri(1, peerKey, "127.0.0.3");

    final var sut = createAddressBook(self);

    sut.addUncheckedPeers(ImmutableSet.of(addr1, addr2, addr3));

    // First round of errors
    assertEquals(addr1, sut.findBestKnownAddressById(peerId).orElseThrow());
    sut.addOrUpdatePeerWithFailedConnection(addr1);
    assertEquals(addr2, sut.findBestKnownAddressById(peerId).orElseThrow());
    sut.addOrUpdatePeerWithFailedConnection(addr2);
    assertEquals(addr3, sut.findBestKnownAddressById(peerId).orElseThrow());
    sut.addOrUpdatePeerWithFailedConnection(addr3);

    // For some time returned list is empty
    assertFalse(sut.findBestKnownAddressById(peerId).isPresent());
    assertFalse(sut.findBestKnownAddressById(peerId).isPresent());

    // Second round of errors
    assertEquals(addr1, sut.findBestKnownAddressById(peerId).orElseThrow());
    sut.addOrUpdatePeerWithFailedConnection(addr1);
    assertEquals(addr2, sut.findBestKnownAddressById(peerId).orElseThrow());
    sut.addOrUpdatePeerWithFailedConnection(addr2);
    assertEquals(addr3, sut.findBestKnownAddressById(peerId).orElseThrow());
    sut.addOrUpdatePeerWithFailedConnection(addr3);
  }

  @Test
  public void address_book_should_return_best_candidate_proxy() {
    final var targetNodeKey = ECKeyPair.generateNew();
    final var targetNode = NodeId.fromPublicKey(targetNodeKey.getPublicKey());

    final var self = createNodeUri(1, "127.0.0.10");
    final var proxy1 = createNodeUri(1, "127.0.0.1");

    final var sut = createAddressBook(self);

    final var proxy1CertData =
        ProxyCertificateData.create(proxy1.getNodeId(), System.currentTimeMillis() + 10000, 1);
    final var proxy1Cert =
        ProxyCertificate.create(proxy1CertData, targetNodeKey.sign(proxy1CertData.hashToSign()));

    sut.addUncheckedPeers(ImmutableSet.of(proxy1));
    sut.updateProxyCertificates(
        proxy1.getNodeId(), ImmutableSet.of(proxy1Cert.verify().orElseThrow()));

    final var result = sut.findBestCandidateProxyFor(targetNode);

    assertEquals(result.orElseThrow(), proxy1);
  }

  private Clock createClock() {
    var clock = mock(Clock.class);
    final var time = new AtomicReference<>(Instant.now());

    when(clock.instant())
        .thenAnswer(
            __ -> {
              var newTime = time.get().plus(Duration.ofSeconds(30));
              time.set(newTime);
              return newTime;
            });

    return clock;
  }

  private AddressBook createAddressBook(RadixNodeUri self) {
    return new AddressBook(
        self, rmock(EventDispatcher.class), new InMemoryAddressBookPersistence(), createClock());
  }

  private static RadixNodeUri createNodeUri(int network, String host) {
    return createNodeUri(network, ECKeyPair.generateNew().getPublicKey(), host);
  }

  private static RadixNodeUri createNodeUri(int network, ECPublicKey publicKey, String host) {
    return RadixNodeUri.fromPubKeyAndAddress(network, publicKey, host, 30000);
  }
}
