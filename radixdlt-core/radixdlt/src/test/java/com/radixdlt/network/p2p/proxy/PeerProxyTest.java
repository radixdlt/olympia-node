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

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.wildfly.common.Assert.assertFalse;

import com.google.common.collect.ImmutableList;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.p2p.liveness.messages.PeerPingMessage;
import com.radixdlt.network.p2p.test.DeterministicP2PNetworkTest;
import com.radixdlt.network.p2p.test.P2PTestNetworkRunner.NodeProps;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Test;

public final class PeerProxyTest extends DeterministicP2PNetworkTest {

  private static final int VALIDATOR_NODE = 0;
  private static final int PROXY_NODE = 1;
  private static final int EXTERNAL_NODE = 2;

  @After
  public void cleanup() {
    testNetworkRunner.cleanup();
  }

  @Test
  public void test_peer_proxy() throws Exception {
    final var nodeAddressing = Addressing.ofNetwork(Network.LOCALNET).forNodes();
    final var validatorKey = ECKeyPair.generateNew();
    final var proxyKey = ECKeyPair.generateNew();

    final var validatorProps = defaultProperties();
    validatorProps.set("network.p2p.proxy.use_proxies", "true");
    validatorProps.set(
        "network.p2p.proxy.authorized_proxies", nodeAddressing.of(proxyKey.getPublicKey()));

    final var proxyProps = defaultProperties();
    proxyProps.set("network.p2p.proxy.enabled", "true");
    proxyProps.set(
        "network.p2p.proxy.authorized_proxied_nodes",
        nodeAddressing.of(validatorKey.getPublicKey()));

    // a network with 3 nodes: a (hidden) validator, a proxy and a default "external" node
    setupTestRunner(
        ImmutableList.of(
            new NodeProps(validatorKey, validatorProps),
            new NodeProps(proxyKey, proxyProps),
            new NodeProps(ECKeyPair.generateNew(), defaultProperties())));

    // proxy node connects to the validator
    testNetworkRunner.addressBook(PROXY_NODE).addUncheckedPeers(Set.of(uriOfNode(VALIDATOR_NODE)));
    testNetworkRunner.peerManager(PROXY_NODE).findOrCreateDirectChannel(nodeIdOf(VALIDATOR_NODE));

    // external node connects to the proxy node
    testNetworkRunner.addressBook(EXTERNAL_NODE).addUncheckedPeers(Set.of(uriOfNode(PROXY_NODE)));
    testNetworkRunner.peerManager(EXTERNAL_NODE).findOrCreateDirectChannel(nodeIdOf(PROXY_NODE));

    processAll();

    // verify all nodes are connected as expected
    assertEquals(1L, testNetworkRunner.peerManager(VALIDATOR_NODE).activeChannels().size());
    assertEquals(2L, testNetworkRunner.peerManager(PROXY_NODE).activeChannels().size());
    assertEquals(1L, testNetworkRunner.peerManager(EXTERNAL_NODE).activeChannels().size());

    // verify that the proxy node has received a valid certificate
    final var receivedCerts =
        testNetworkRunner.proxyCertManager(PROXY_NODE).getReceivedProxyCertificates();
    assertEquals(1L, receivedCerts.size());
    final var receivedCert = receivedCerts.iterator().next().verify().get();
    assertEquals(nodeIdOf(VALIDATOR_NODE), receivedCert.signer());
    assertEquals(nodeIdOf(PROXY_NODE), receivedCert.grantee());
    assertTrue(receivedCert.expiresAt() > System.currentTimeMillis());

    // verify that the proxy has forwarded its certificate to an external node
    final var verifiedProxies =
        testNetworkRunner
            .proxyCertManager(EXTERNAL_NODE)
            .getVerifiedProxiesForNode(nodeIdOf(VALIDATOR_NODE));
    assertEquals(1L, verifiedProxies.size());

    // verify that the message can be routed from external node, through proxy, to the validator
    testNetworkRunner
        .messageCentral(EXTERNAL_NODE)
        .send(nodeIdOf(VALIDATOR_NODE), new PeerPingMessage());

    waitForMessagesAndProcessAll();

    // await until a message is received by the validator
    await()
        .atMost(Duration.ofSeconds(2))
        .until(
            () ->
                testNetworkRunner.counter(VALIDATOR_NODE, CounterType.MESSAGES_INBOUND_PROCESSED)
                    == 1L);

    // proxy node has processed and forwarded a single message
    assertEquals(1L, testNetworkRunner.counter(PROXY_NODE, CounterType.MESSAGES_INBOUND_PROCESSED));
    assertEquals(
        1L,
        testNetworkRunner.counter(PROXY_NODE, CounterType.NETWORKING_ROUTING_FORWARDED_MESSAGES));

    // verify that the message can be routed from the validator node, through proxy, to the external
    // node
    testNetworkRunner
        .messageCentral(VALIDATOR_NODE)
        .send(nodeIdOf(EXTERNAL_NODE), new PeerPingMessage());

    waitForMessagesAndProcessAll();

    // await until a message is received by the external node
    await()
        .atMost(Duration.ofSeconds(2))
        .until(
            () ->
                testNetworkRunner.counter(EXTERNAL_NODE, CounterType.MESSAGES_INBOUND_PROCESSED)
                    == 1L);

    // proxy node has processed and forwarded one more message
    assertEquals(2L, testNetworkRunner.counter(PROXY_NODE, CounterType.MESSAGES_INBOUND_PROCESSED));
    assertEquals(
        2L,
        testNetworkRunner.counter(PROXY_NODE, CounterType.NETWORKING_ROUTING_FORWARDED_MESSAGES));
  }

  @Test
  public void test_peer_proxy_disabled() throws Exception {
    final var nodeAddressing = Addressing.ofNetwork(Network.LOCALNET).forNodes();
    final var validatorKey = ECKeyPair.generateNew();
    final var proxyKey = ECKeyPair.generateNew();

    final var validatorProps = defaultProperties();
    validatorProps.set("network.p2p.proxy.use_proxies", "true");
    validatorProps.set(
        "network.p2p.proxy.authorized_proxies", nodeAddressing.of(proxyKey.getPublicKey()));

    final var proxyProps = defaultProperties();
    // proxied peer is configured, but proxy is not enabled
    proxyProps.set(
        "network.p2p.proxy.authorized_proxied_nodes",
        nodeAddressing.of(validatorKey.getPublicKey()));

    // a network with 3 nodes: a (hidden) validator, a proxy and a default "external" node
    setupTestRunner(
        ImmutableList.of(
            new NodeProps(validatorKey, validatorProps),
            new NodeProps(proxyKey, proxyProps),
            new NodeProps(ECKeyPair.generateNew(), defaultProperties())));

    // proxy node connects to the validator
    testNetworkRunner.addressBook(PROXY_NODE).addUncheckedPeers(Set.of(uriOfNode(VALIDATOR_NODE)));
    testNetworkRunner.peerManager(PROXY_NODE).findOrCreateDirectChannel(nodeIdOf(VALIDATOR_NODE));

    // external node connects to the proxy node
    testNetworkRunner.addressBook(EXTERNAL_NODE).addUncheckedPeers(Set.of(uriOfNode(PROXY_NODE)));
    testNetworkRunner.peerManager(EXTERNAL_NODE).findOrCreateDirectChannel(nodeIdOf(PROXY_NODE));

    processAll();

    // verify all nodes are connected as expected
    assertEquals(1L, testNetworkRunner.peerManager(VALIDATOR_NODE).activeChannels().size());
    assertEquals(2L, testNetworkRunner.peerManager(PROXY_NODE).activeChannels().size());
    assertEquals(1L, testNetworkRunner.peerManager(EXTERNAL_NODE).activeChannels().size());

    // verify that the proxy node hasn't processed a received certificate
    final var receivedCerts =
        testNetworkRunner.proxyCertManager(PROXY_NODE).getReceivedProxyCertificates();
    assertEquals(0L, receivedCerts.size());

    // verify that the proxy hasn't forwarded the received certificate to an external node
    final var verifiedProxies =
        testNetworkRunner
            .proxyCertManager(EXTERNAL_NODE)
            .getVerifiedProxiesForNode(nodeIdOf(VALIDATOR_NODE));
    assertEquals(0L, verifiedProxies.size());
  }

  @Test
  public void test_use_proxy_if_direct_connection_is_not_allowed() throws Exception {
    final var nodeAddressing = Addressing.ofNetwork(Network.LOCALNET).forNodes();
    final var validatorKey = ECKeyPair.generateNew();
    final var proxyKey = ECKeyPair.generateNew();

    final var validatorProps = defaultProperties();
    validatorProps.set("network.p2p.proxy.use_proxies", "true");
    validatorProps.set(
        "network.p2p.proxy.authorized_proxies", nodeAddressing.of(proxyKey.getPublicKey()));
    // validator can only connect to the proxy node
    validatorProps.set("network.p2p.use_peer_allow_list", "true");
    validatorProps.set("network.p2p.peer_allow_list", nodeAddressing.of(proxyKey.getPublicKey()));

    final var proxyProps = defaultProperties();
    proxyProps.set("network.p2p.proxy.enabled", "true");
    proxyProps.set(
        "network.p2p.proxy.authorized_proxied_nodes",
        nodeAddressing.of(validatorKey.getPublicKey()));

    // a network with 3 nodes: a (hidden) validator, a proxy and a default "external" node
    setupTestRunner(
        ImmutableList.of(
            new NodeProps(validatorKey, validatorProps),
            new NodeProps(proxyKey, proxyProps),
            new NodeProps(ECKeyPair.generateNew(), defaultProperties())));

    // the validator node has both the proxy and the external node in its address book
    testNetworkRunner.addressBook(VALIDATOR_NODE).addUncheckedPeers(Set.of(uriOfNode(PROXY_NODE)));
    testNetworkRunner
        .addressBook(VALIDATOR_NODE)
        .addUncheckedPeers(Set.of(uriOfNode(EXTERNAL_NODE)));

    // the proxy node has external node in its address book
    testNetworkRunner.addressBook(PROXY_NODE).addUncheckedPeers(Set.of(uriOfNode(EXTERNAL_NODE)));

    // the validator sends a message to the external node
    testNetworkRunner
        .messageCentral(VALIDATOR_NODE)
        .send(nodeIdOf(EXTERNAL_NODE), new PeerPingMessage());

    waitForMessagesAndProcessAll(2);

    // proxy node should forward a single message
    await()
        .atMost(Duration.ofSeconds(2))
        .until(
            () ->
                testNetworkRunner.counter(
                        PROXY_NODE, CounterType.NETWORKING_ROUTING_FORWARDED_MESSAGES)
                    == 1L);

    waitForMessagesAndProcessAll();

    // validator should only be connected to the proxy node
    final var validatorConnectedPeers =
        testNetworkRunner.peerManager(VALIDATOR_NODE).activeChannels().stream()
            .map(PeerChannel::getRemoteNodeId)
            .collect(Collectors.toSet());
    assertTrue(validatorConnectedPeers.contains(nodeIdOf(PROXY_NODE)));
    assertFalse(validatorConnectedPeers.contains(nodeIdOf(EXTERNAL_NODE)));

    // external node should be connected to the proxy node
    final var externalNodePeers =
        testNetworkRunner.peerManager(EXTERNAL_NODE).activeChannels().stream()
            .map(PeerChannel::getRemoteNodeId)
            .collect(Collectors.toSet());
    assertTrue(externalNodePeers.contains(nodeIdOf(PROXY_NODE)));

    // the external node sends a message to the validator
    testNetworkRunner
        .messageCentral(EXTERNAL_NODE)
        .send(nodeIdOf(VALIDATOR_NODE), new PeerPingMessage());

    waitForMessagesAndProcessAll();

    // proxy node should forward one more message
    await()
        .atMost(Duration.ofSeconds(2))
        .until(
            () ->
                testNetworkRunner.counter(
                        PROXY_NODE, CounterType.NETWORKING_ROUTING_FORWARDED_MESSAGES)
                    == 2L);
  }

  private void waitForMessagesAndProcessAll() {
    waitForMessagesAndProcessAll(1);
  }

  private void waitForMessagesAndProcessAll(int times) {
    // a small hack to wait for the processing thread
    for (int i = 0; i < times; i++) {
      await()
          .atMost(Duration.ofSeconds(4))
          .until(() -> !testNetworkRunner.getDeterministicNetwork().allMessages().isEmpty());
      processAll();
    }
  }
}
