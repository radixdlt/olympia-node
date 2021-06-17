/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.p2p;

import com.radixdlt.network.p2p.test.DeterministicP2PNetworkTest;
import org.junit.After;
import org.junit.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PeerManagerTest extends DeterministicP2PNetworkTest {

	@After
	public void cleanup() {
		testNetworkRunner.cleanup();
	}

	@Test
	public void when_findOrCreateChannel_then_should_create_if_not_exists() throws Exception {
		setupTestRunner(3, defaultProperties());

		testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(uriOfNode(1)));
		final var channelFuture = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(uriOfNode(1).getNodeId());

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

		testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(uriOfNode(1), uriOfNode(2), uriOfNode(3), uriOfNode(4)));
		final var channel1Future = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(uriOfNode(1).getNodeId());

		final var channel2Future = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(uriOfNode(2).getNodeId());

		final var channel3Future = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(uriOfNode(3).getNodeId());

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

		final var channel4Future = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(uriOfNode(4).getNodeId());

		processAll();

		assertEquals(3L, testNetworkRunner.peerManager(0).activeChannels().size());
		assertEquals(1L, testNetworkRunner.peerManager(1).activeChannels().size());
		assertEquals(0L, testNetworkRunner.peerManager(2).activeChannels().size()); // node2 should be disconnected
		assertEquals(1L, testNetworkRunner.peerManager(3).activeChannels().size());
		assertEquals(1L, testNetworkRunner.peerManager(4).activeChannels().size());
	}

	@Test
	public void should_not_connect_to_banned_peers() throws Exception {
		final var props = defaultProperties();
		setupTestRunner(5, props);

		testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(uriOfNode(1), uriOfNode(2), uriOfNode(3), uriOfNode(4)));
		testNetworkRunner.addressBook(1).addUncheckedPeers(Set.of(uriOfNode(0)));

		// ban node1 and node3 on node0
		testNetworkRunner.getInstance(0, PeerControl.class).banPeer(uriOfNode(1).getNodeId(), Duration.ofHours(1));
		testNetworkRunner.getInstance(0, PeerControl.class).banPeer(uriOfNode(3).getNodeId(), Duration.ofHours(1));

		// try outbound connection (to node3)
		final var channel1Future = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(uriOfNode(3).getNodeId());

		processAll();

		assertTrue(channel1Future.isCompletedExceptionally());
		assertEquals(0L, testNetworkRunner.peerManager(0).activeChannels().size());
		assertEquals(0L, testNetworkRunner.peerManager(3).activeChannels().size());

		// try inbound connection (from node1)

		final var channel2Future = testNetworkRunner.peerManager(1)
			.findOrCreateChannel(uriOfNode(0).getNodeId());

		processAll();

		assertEquals(0L, testNetworkRunner.peerManager(0).activeChannels().size());
		assertEquals(0L, testNetworkRunner.peerManager(1).activeChannels().size());
	}

	@Test
	public void should_disconnect_just_banned_peer() throws Exception {
		final var props = defaultProperties();
		setupTestRunner(2, props);

		testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(uriOfNode(1)));

		final var channel1Future = testNetworkRunner.peerManager(0)
		.findOrCreateChannel(uriOfNode(1).getNodeId());

		processAll();

		// assert the connections is successful
		assertTrue(channel1Future.isDone());
		assertEquals(1L, testNetworkRunner.peerManager(0).activeChannels().size());
		assertEquals(1L, testNetworkRunner.peerManager(1).activeChannels().size());

		// ban node0 on node1
		testNetworkRunner.getInstance(1, PeerControl.class).banPeer(uriOfNode(0).getNodeId(), Duration.ofHours(1));

		processAll();

		// assert connection closed
		assertEquals(0L, testNetworkRunner.peerManager(0).activeChannels().size());
		assertEquals(0L, testNetworkRunner.peerManager(1).activeChannels().size());
	}
}
