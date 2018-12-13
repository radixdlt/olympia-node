package com.radixdlt.client.core.network;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;

public class PeersFromSeedTest {
	@Test
	public void testFindPeers() {
		RadixPeer peer = mock(RadixPeer.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		NodeRunnerData data = mock(NodeRunnerData.class);
		when(peer.getRadixClient()).thenReturn(Optional.of(client));
		when(peer.getLocation()).thenReturn("somewhere");
		when(peer.connect()).thenReturn(client);
		when(client.getInfo()).thenReturn(Single.just(data));
		when(client.getLivePeers()).thenReturn(Single.just(Collections.emptyList()));

		TestObserver<RadixPeer> testObserver = TestObserver.create();
		PeersFromSeed peersFromSeed = new PeersFromSeed(peer);
		peersFromSeed.findPeers().subscribe(testObserver);

		testObserver.assertValue(p -> p.getLocation().equals("somewhere"));
	}


	@Test
	public void testFindPeersFail() {
		RadixPeer peer = mock(RadixPeer.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		when(peer.getRadixClient()).thenReturn(Optional.of(client));
		when(peer.getLocation()).thenReturn("somewhere");
		when(peer.connect()).thenReturn(client);
		when(client.getInfo()).thenReturn(Single.error(new IOException()));
		when(client.getLivePeers()).thenReturn(Single.error(new IOException()));

		TestObserver<RadixPeer> testObserver = TestObserver.create();
		PeersFromSeed peersFromSeed = new PeersFromSeed(peer);
		peersFromSeed.findPeers().subscribe(testObserver);

		testObserver.assertError(e -> e instanceof IOException);
	}
}