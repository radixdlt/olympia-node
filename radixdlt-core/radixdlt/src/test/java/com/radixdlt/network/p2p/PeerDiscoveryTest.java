package com.radixdlt.network.p2p;

import com.google.inject.Key;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.discovery.DiscoverPeers;
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
