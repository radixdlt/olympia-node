package com.radixdlt.client.core.ledger;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.ledger.selector.ConnectionAliveFilter;
import com.radixdlt.client.core.ledger.selector.GetFirstSelector;
import com.radixdlt.client.core.network.RadixClientStatus;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixPeer;
import com.radixdlt.client.core.network.RadixPeerState;
import com.radixdlt.client.core.network.WebSocketException;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RadixClientSupplierTest {
	@Test
	public void failedNodeConnectionTest() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixNetwork network = mock(RadixNetwork.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);

		RadixPeer peer = mock(RadixPeer.class);
		RadixPeerState peerState = mock(RadixPeerState.class);
		when(network.getNetworkState()).thenReturn(Observable.just(new RadixNetworkState(ImmutableMap.of(peer, peerState))));

		RadixClientSupplier clientSelector = new RadixClientSupplier(network, config);
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		clientSelector.getRadixClients(Collections.singleton(1L)).subscribe(testObserver);

		testObserver.assertNoErrors();
		testObserver.assertNoValues();
	}

	@Test
	public void dontConnectToAllNodesTest() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);

		List<AbstractMap.SimpleImmutableEntry<RadixPeer, RadixPeerState>> peers = IntStream.range(0, 100).mapToObj(i -> {
			RadixPeer peer = mock(RadixPeer.class);
			RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
			when(peer.servesShards(any())).thenReturn(Single.just(true));
			when(peer.getRadixClient()).thenReturn(Optional.of(client));

			RadixPeerState peerState = mock(RadixPeerState.class);
			when(peerState.getVersion()).thenReturn(Optional.of(1)); // TODO this should be a constant at least, not sure yet
			when(peerState.getUniverseConfig()).thenReturn(Optional.of(config));
			when(client.universe()).thenReturn(Single.just(config));

			if (i == 0) {
				when(peerState.getStatus()).thenReturn(RadixClientStatus.OPEN);
			} else {
				when(peerState.getStatus()).thenReturn(RadixClientStatus.CLOSED);
			}
			return new AbstractMap.SimpleImmutableEntry<>(peer, peerState);
		}).collect(Collectors.toList());

		RadixNetwork network = mock(RadixNetwork.class);
		when(network.getNetworkState()).thenReturn(Observable.fromIterable(peers).map(e
				-> new RadixNetworkState(ImmutableMap.of(e.getKey(), e.getValue()))));
		RadixClientSupplier selector = new RadixClientSupplier(
				network, new GetFirstSelector(), new ConnectionAliveFilter());
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		selector.getRadixClient().subscribe(testObserver);
		testObserver.awaitTerminalEvent();
		testObserver.assertValue(peers.get(0).getKey().getRadixClient().get());

		verify(peers.get(99).getKey().getRadixClient().get(), times(0)).universe();
	}

	@Test
	public void whenFirstNodeFailsThenSecondNodeShouldConnect() {
		RadixNetwork network = mock(RadixNetwork.class);
		RadixPeer badPeer = mock(RadixPeer.class);
		RadixJsonRpcClient badClient = mock(RadixJsonRpcClient.class);
		when(badPeer.getRadixClient()).thenReturn(Optional.of(badClient));
		RadixPeerState badPeerState = mock(RadixPeerState.class);
		when(badPeerState.getStatus()).thenReturn(RadixClientStatus.CLOSED);

		RadixPeer goodPeer = mock(RadixPeer.class);
		RadixJsonRpcClient goodClient = mock(RadixJsonRpcClient.class);
		when(goodPeer.getRadixClient()).thenReturn(Optional.of(goodClient));
		RadixPeerState goodPeerState = mock(RadixPeerState.class);
		when(goodPeerState.getStatus()).thenReturn(RadixClientStatus.OPEN);

		when(network.getNetworkState()).thenReturn(Observable.fromArray(
				new RadixNetworkState(ImmutableMap.of(badPeer, badPeerState)),
				new RadixNetworkState(ImmutableMap.of(goodPeer, goodPeerState))));

		RadixClientSupplier selector = new RadixClientSupplier(network, new GetFirstSelector(), new ConnectionAliveFilter());
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		selector.getRadixClient().subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertValue(goodClient);
	}

	@Test
	public void testValidClient() {
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		RadixPeer peer = mock(RadixPeer.class);
		when(peer.getRadixClient()).thenReturn(Optional.of(client));

		RadixPeerState peerState = mock(RadixPeerState.class);
		when(peerState.getStatus()).thenReturn(RadixClientStatus.OPEN);

		RadixNetwork network = mock(RadixNetwork.class);
		when(network.getNetworkState()).thenReturn(Observable.just(new RadixNetworkState(ImmutableMap.of(peer, peerState))));
		RadixClientSupplier selector = new RadixClientSupplier(network, new GetFirstSelector(), new ConnectionAliveFilter());

		Assert.assertEquals(client, selector.getRadixClient().blockingGet());
	}


	/**
	 * RadixNetwork class should protect subscribers from network level exceptions
	 */
	@Test
	public void testPeerDiscoveryFail() {
		RadixNetwork network = new RadixNetwork(() -> Observable.error(new WebSocketException()));
		TestObserver<RadixPeerState> observer = TestObserver.create();
		network.getStatusUpdates().subscribe(observer);
		network.connectAndGetStatusUpdates().subscribe();
		observer.assertNoErrors();
	}
}