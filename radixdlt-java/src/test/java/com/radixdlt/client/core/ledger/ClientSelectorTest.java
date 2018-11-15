package com.radixdlt.client.core.ledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

public class ClientSelectorTest {
	@Test
	public void failedNodeConnectionTest() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixNetwork network = mock(RadixNetwork.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.getUniverse()).thenReturn(Single.error(new IOException()));
		when(network.getRadixClients(any(Set.class))).thenReturn(Observable.concat(Observable.just(client), Observable.never()));

		ClientSelector clientSelector = new ClientSelector(config, network, false);
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		clientSelector.getRadixClient(1L).subscribe(testObserver);

		testObserver.assertNoErrors();
		testObserver.assertNoValues();
	}

	@Test
	public void dontConnectToAllNodesTest() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);

		RadixNetwork network = mock(RadixNetwork.class);
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
		when(network.getRadixClients(any(Set.class)))
			.thenReturn(Observable.fromIterable(clients));

		ClientSelector clientSelector = new ClientSelector(config, network, false);
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		clientSelector.getRadixClient(1L).subscribe(testObserver);
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

		when(network.getRadixClients(any(Set.class))).thenReturn(
			Observable.concat(Observable.just(badClient), Observable.just(goodClient), Observable.never()));

		ClientSelector clientSelector = new ClientSelector(config, network, false);
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		clientSelector.getRadixClient(1L).subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertValue(goodClient);
	}
}