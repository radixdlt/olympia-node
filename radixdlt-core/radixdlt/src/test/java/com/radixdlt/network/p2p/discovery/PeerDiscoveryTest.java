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

package com.radixdlt.network.p2p.discovery;

import com.google.inject.Key;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.test.DeterministicP2PNetworkTest;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public final class PeerDiscoveryTest extends DeterministicP2PNetworkTest {

	@Test
	public void when_discover_peers_then_should_connect_to_some_peers() throws Exception {
		setupTestRunner(5, defaultConfig());

		// add 4 peers to the addr book
		testNetworkRunner.addressBook(0).addUncheckedPeers(Set.of(
			testNetworkRunner.getUri(1),
			testNetworkRunner.getUri(2),
			testNetworkRunner.getUri(3),
			testNetworkRunner.getUri(4)
		));

		testNetworkRunner.getInstance(0, new Key<EventDispatcher<DiscoverPeers>>() { })
			.dispatch(DiscoverPeers.create());

		processForCount(10);

		// with 10 slots (default), max num of peers to connect is 3 (10/2 - 2)
		assertEquals(3L, testNetworkRunner.peerManager(0).activePeers().size());
	}

}
