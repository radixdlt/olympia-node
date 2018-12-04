package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixPeer;
import com.radixdlt.client.core.network.RadixClientStatus;
import com.radixdlt.client.core.network.WebSocketException;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RadixClientSupplierTest {
	@Test
	public void failedNodeConnectionTest() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixNetwork network = mock(RadixNetwork.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		RadixClientSupplier selector = new RadixClientSupplier(network);

		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.getUniverse()).thenReturn(Single.error(new IOException()));
		when(selector.getRadixClients(any(Set.class))).thenReturn(Observable.concat(Observable.just(client), Observable.never()));

		RadixClientSupplier clientSelector = new RadixClientSupplier(network, config);
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		clientSelector.getRadixClient(1L).subscribe(testObserver);

		testObserver.assertNoErrors();
		testObserver.assertNoValues();
	}

	@Test
	public void dontConnectToAllNodesTest() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);

		RadixNetwork network = mock(RadixNetwork.class);
		RadixClientSupplier selector = spy(new RadixClientSupplier(network));

		List<RadixJsonRpcClient> clients = IntStream.range(0, 100).mapToObj(i -> {
			RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
			when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.CLOSED));
			if (i == 0) {
				when(client.getUniverse()).thenReturn(Single.timer(1, TimeUnit.SECONDS).map(t -> config));
			} else {
				when(client.getUniverse()).thenReturn(Single.never());
			}
			return client;
		}).collect(Collectors.toList());
		doReturn(Observable.fromIterable(clients)).when(selector).getRadixClients(any());

		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		selector.getRadixClient(1L).subscribe(testObserver);
		testObserver.awaitTerminalEvent();
		testObserver.assertValue(clients.get(0));

		verify(clients.get(99), times(0)).getUniverse();
	}

	@Test
	public void whenFirstNodeFailsThenSecondNodeShouldConnect() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixNetwork network = mock(RadixNetwork.class);
		RadixJsonRpcClient badClient = mock(RadixJsonRpcClient.class);
		when(badClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(badClient.getUniverse()).thenReturn(Single.error(new IOException()));

		RadixJsonRpcClient goodClient = mock(RadixJsonRpcClient.class);
		when(goodClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(goodClient.getUniverse()).thenReturn(Single.just(mock(RadixUniverseConfig.class)));
		RadixClientSupplier selector = new RadixClientSupplier(network);

		when(selector.getRadixClients(any(Set.class))).thenReturn(
				Observable.concat(Observable.just(badClient), Observable.just(goodClient), Observable.never()));

		RadixClientSupplier clientSelector = new RadixClientSupplier(network, config);
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		clientSelector.getRadixClient(1L).subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertValue(goodClient);
	}

	@Test
	public void testGetClientsMultipleTimes() {
		RadixNetwork network = new RadixNetwork(() -> Observable.just(
				new RadixPeer("1", false, 8080),
				new RadixPeer("2", false, 8080),
				new RadixPeer("3", false, 8080)
		));
		RadixClientSupplier selector = new RadixClientSupplier(network);

		IntStream.range(0, 10).forEach(i ->
				selector.getRadixClients()
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
		when(peer.servesShards(any())).thenReturn(Single.just(true));
		when(peer.getRadixClient()).thenReturn(client);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.checkAPIVersion()).thenReturn(Single.just(false));

		RadixNetwork network = new RadixNetwork(() -> Observable.just(peer));

		RadixClientSupplier selector = new RadixClientSupplier(network);
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		selector.getRadixClient(0L).subscribe(testObserver);
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
		when(peer.servesShards(any())).thenReturn(Single.just(true));
		when(peer.getRadixClient()).thenReturn(client);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.checkAPIVersion()).thenReturn(Single.just(true));
		when(client.getUniverse()).thenReturn(Single.just(config));

		RadixNetwork network = new RadixNetwork(() -> Observable.just(peer));
		RadixClientSupplier selector = new RadixClientSupplier(network);

		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		selector.getRadixClient(0L).subscribe(testObserver);
		testObserver.assertValue(client);
	}


	/**
	 * RadixNetwork class should protect subscribers from network level exceptions
	 */
	@Test
	public void testPeerDiscoveryFail() {
		RadixNetwork network = new RadixNetwork(() -> Observable.error(new WebSocketException()));
		TestObserver<AbstractMap.SimpleImmutableEntry<RadixPeer, RadixClientStatus>> observer = TestObserver.create();
		network.getStatusUpdates().subscribe(observer);
		network.connectAndGetStatusUpdates().subscribe();
		observer.assertNoErrors();
	}
}