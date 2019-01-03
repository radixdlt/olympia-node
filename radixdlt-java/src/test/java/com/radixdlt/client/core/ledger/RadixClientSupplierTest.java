package com.radixdlt.client.core.ledger;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.ledger.selector.ConnectionAliveFilter;
import com.radixdlt.client.core.ledger.selector.GetFirstSelector;
import com.radixdlt.client.core.network.RadixClientStatus;
import com.radixdlt.client.core.network.RadixClientSupplier;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixPeer;
import com.radixdlt.client.core.network.RadixPeerState;
import com.radixdlt.client.core.network.WebSocketClient;
import com.radixdlt.client.core.network.WebSocketException;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RadixClientSupplierTest {
	@Test
	public void failedNodeConnectionTest() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixNetwork network = mock(RadixNetwork.class);

		RadixPeer peer = mock(RadixPeer.class);
		when(network.getNetworkState())
			.thenReturn(Observable.just(new RadixNetworkState(ImmutableMap.of(peer, RadixClientStatus.DISCONNECTED))));

		RadixClientSupplier clientSelector = new RadixClientSupplier(network, config);
		TestObserver<RadixPeer> testObserver = TestObserver.create();
		clientSelector.getRadixClients(Collections.singleton(1L)).subscribe(testObserver);

		testObserver.assertNoErrors();
		testObserver.assertNoValues();
	}

	@Test
	public void dontConnectToAllNodesTest() {
		RadixPeer connectedPeer = mock(RadixPeer.class);

		Map<RadixPeer, RadixClientStatus> networkStateMap = IntStream.range(0, 100).boxed().collect(Collectors.toMap(
			i -> {
				if (i == 0) {
					return connectedPeer;
				}

				RadixPeer peer = mock(RadixPeer.class);
				return peer;
			},
			i -> i == 0 ? RadixClientStatus.CONNECTED : RadixClientStatus.DISCONNECTED
		));

		RadixNetwork network = mock(RadixNetwork.class);
		RadixNetworkState networkState = new RadixNetworkState(networkStateMap);
		when(network.getNetworkState()).thenReturn(Observable.concat(Observable.just(networkState), Observable.never()));

		RadixClientSupplier selector = new RadixClientSupplier(network, new GetFirstSelector(), new ConnectionAliveFilter());
		TestObserver<RadixPeer> testObserver = TestObserver.create();
		selector.getRadixClient().subscribe(testObserver);
		testObserver.awaitTerminalEvent();
		testObserver.assertValue(connectedPeer);

		verify(network, times(0)).connect(any());
	}

	@Test
	public void whenFirstNodeFailsThenSecondNodeShouldConnect() {
		RadixNetwork network = mock(RadixNetwork.class);
		RadixPeer badPeer = mock(RadixPeer.class);
		RadixPeer goodPeer = mock(RadixPeer.class);

		ReplaySubject<RadixNetworkState> networkState = ReplaySubject.create();

		networkState.onNext(new RadixNetworkState(ImmutableMap.of(
			badPeer, RadixClientStatus.DISCONNECTED,
			goodPeer, RadixClientStatus.DISCONNECTED
		)));

		when(network.getNetworkState()).thenReturn(networkState);

		doAnswer(invocation -> {
			networkState.onNext(new RadixNetworkState(ImmutableMap.of(
				badPeer, RadixClientStatus.FAILED,
				goodPeer, RadixClientStatus.DISCONNECTED
			)));
			return null;
		}).when(network).connect(badPeer);

		doAnswer(invocation -> {
			networkState.onNext(new RadixNetworkState(ImmutableMap.of(
				badPeer, RadixClientStatus.FAILED,
				goodPeer, RadixClientStatus.CONNECTED
			)));
			return null;
		}).when(network).connect(goodPeer);


		RadixClientSupplier selector = new RadixClientSupplier(network, new GetFirstSelector(), new ConnectionAliveFilter());
		TestObserver<RadixPeer> testObserver = TestObserver.create();
		selector.getRadixClient().subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertValue(goodPeer);
	}

	@Test
	public void testValidClient() {
		RadixPeer peer = mock(RadixPeer.class);
		WebSocketClient ws = mock(WebSocketClient.class);
		when(ws.getMessages()).thenReturn(Observable.never());
		when(ws.sendMessage(any())).thenReturn(true);

		RadixNetwork network = mock(RadixNetwork.class);
		when(network.getWsChannel(any())).thenReturn(ws);
		when(network.getNetworkState()).thenReturn(Observable.just(new RadixNetworkState(ImmutableMap.of(peer, RadixClientStatus.CONNECTED))));
		RadixClientSupplier selector = new RadixClientSupplier(network, new GetFirstSelector(), new ConnectionAliveFilter());

		Assert.assertEquals(peer, selector.getRadixClient().blockingGet());
	}
}