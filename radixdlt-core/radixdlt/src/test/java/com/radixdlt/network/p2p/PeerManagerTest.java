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
		setupTestRunner(3, defaultConfig());

		final var node1Uri = testNetworkRunner.getUri(1);

		testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(node1Uri));
		final var channel = testNetworkRunner.peerManager(0)
			.findOrCreateChannel(node1Uri.getNodeId());

		processForCount(3);

		assertEquals(node1Uri, channel.get().getUri().get());

		assertEquals(1L, testNetworkRunner.peerManager(0).activePeers().size());
		assertEquals(1L, testNetworkRunner.peerManager(1).activePeers().size());
	}

}
