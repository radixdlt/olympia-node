package com.radixdlt.client.core.network;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import org.junit.Test;

public class PeersFromSeedTest {
	@Test
	public void testFindPeers() {
		RadixPeer peer = mock(RadixPeer.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		NodeRunnerData data = mock(NodeRunnerData.class);
		when(peer.getRadixClient()).thenReturn(client);
		when(peer.getLocation()).thenReturn("somewhere");
		when(client.getSelf()).thenReturn(Single.just(data));
		when(client.getLivePeers()).thenReturn(Single.just(Collections.emptyList()));

		TestObserver<RadixPeer> testObserver = TestObserver.create();
		PeersFromSeed peersFromSeed = new PeersFromSeed(peer);
		peersFromSeed.findPeers().subscribe(testObserver);

		testObserver.assertValue(p -> p.getLocation().equals("somewhere"));
	}
}