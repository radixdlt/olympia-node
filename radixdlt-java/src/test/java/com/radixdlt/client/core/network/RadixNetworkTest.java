package com.radixdlt.client.core.network;

public class RadixNetworkTest {

	@Test
	public void testGetClientsMultipleTimes() {
		RadixNetwork network = new RadixNetwork(() -> Observable.just(
			new RadixPeer("1", false, 8080),
			new RadixPeer("2", false, 8080),
			new RadixPeer("3", false, 8080)
		));

		IntStream.range(0, 10).forEach(i ->
			network.getRadixClients()
				.map(RadixJsonRpcClient::getLocation)
				.test()
				.assertValueAt(0, "http://1:8080/rpc")
				.assertValueAt(1, "http://2:8080/rpc")
				.assertValueAt(2, "http://3:8080/rpc")
		);
	}

	@Test
	public void testAPIMismatch() {
		RadixPeer peer = mock(RadixPeer.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		when(peer.servesShards(any())).thenReturn(Maybe.just(peer));
		when(peer.getRadixClient()).thenReturn(client);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.checkAPIVersion()).thenReturn(Single.just(false));

		RadixNetwork network = new RadixNetwork(() -> Observable.just(peer));

		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		network.getRadixClients(0L).subscribe(testObserver);
		testObserver
			.assertComplete()
			.assertNoErrors()
			.assertNoValues();
	}

	@Test
	public void testValidClient() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixPeer peer = mock(RadixPeer.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		when(peer.servesShards(any())).thenReturn(Maybe.just(peer));
		when(peer.getRadixClient()).thenReturn(client);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.checkAPIVersion()).thenReturn(Single.just(true));
		when(client.getUniverse()).thenReturn(Single.just(config));

		RadixNetwork network = new RadixNetwork(() -> Observable.just(peer));

		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		network.getRadixClients(0L).subscribe(testObserver);
		testObserver.assertValue(client);
	}


	/**
	 * RadixNetwork class should protect subscribers from network level exceptions
	 */
	@Test
	public void testPeerDiscoveryFail() {
		RadixNetwork network = new RadixNetwork(() -> Observable.error(new WebSocketException()));
		TestObserver<SimpleImmutableEntry<RadixJsonRpcClient, RadixClientStatus>> observer = TestObserver.create();
		network.getStatusUpdates().subscribe(observer);
		network.connectAndGetStatusUpdates().subscribe();
		observer.assertNoErrors();
	}
}