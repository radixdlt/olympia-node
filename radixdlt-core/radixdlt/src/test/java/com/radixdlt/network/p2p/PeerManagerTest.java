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
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public final class PeerManagerTest extends DeterministicP2PNetworkTest {

	@Test
	public void when_findOrCreateChannel_then_should_create_if_not_exists() throws Exception {
		setupTestRunner(3, defaultProperties());

		final var node1Uri = testNetworkRunner.getUri(1);

		testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(node1Uri));
		final var channelFuture = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(node1Uri.getNodeId());

		processForCount(3);

		assertEquals(node1Uri, channelFuture.get().getUri().get());

		assertEquals(1L, testNetworkRunner.peerManager(0).activePeers().size());
		assertEquals(1L, testNetworkRunner.peerManager(1).activePeers().size());
	}

	@Test
	public void should_disconnect_the_least_used_channels_when_over_limit() throws Exception {
		final var props = defaultProperties();
		props.set("network.p2p.max_outbound_channels", 3); // 3 outbound channels allowed
		setupTestRunner(5, props);

		final var node1Uri = testNetworkRunner.getUri(1);
		final var node2Uri = testNetworkRunner.getUri(2);
		final var node3Uri = testNetworkRunner.getUri(3);
		final var node4Uri = testNetworkRunner.getUri(4);

		testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(node1Uri, node2Uri, node3Uri, node4Uri));
		final var channel1Future = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(node1Uri.getNodeId());

		final var channel2Future = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(node2Uri.getNodeId());

		final var channel3Future = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(node3Uri.getNodeId());

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
			.findOrCreateChannel(node4Uri.getNodeId());

		processAll();

		assertEquals(3L, testNetworkRunner.peerManager(0).activePeers().size());
		assertEquals(1L, testNetworkRunner.peerManager(1).activePeers().size());
		assertEquals(0L, testNetworkRunner.peerManager(2).activePeers().size()); // node2 should be disconnected
		assertEquals(1L, testNetworkRunner.peerManager(3).activePeers().size());
		assertEquals(1L, testNetworkRunner.peerManager(4).activePeers().size());
	}

}
