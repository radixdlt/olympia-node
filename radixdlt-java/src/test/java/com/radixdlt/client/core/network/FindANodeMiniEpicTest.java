package com.radixdlt.client.core.network;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.ledger.selector.GetFirstSelector;
import com.radixdlt.client.core.ledger.selector.RandomSelector;
import com.radixdlt.client.core.network.epics.FindANodeMiniEpic;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import java.util.Map;
import org.junit.Test;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FindANodeMiniEpicTest {
	@Test
	public void testValidClient() {
		RadixPeer peer = mock(RadixPeer.class);
		WebSocketClient ws = mock(WebSocketClient.class);
		when(ws.getMessages()).thenReturn(Observable.never());
		when(ws.sendMessage(any())).thenReturn(true);

		RadixNetwork network = mock(RadixNetwork.class);
		when(network.getWsChannel(any())).thenReturn(ws);

		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(network, new GetFirstSelector());
		TestObserver<RadixPeer> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			Observable.just(new RadixNetworkState(ImmutableMap.of(peer, RadixClientStatus.CONNECTED)))
		)
		.subscribe(testObserver);

		testObserver.assertValue(peer);
	}

	@Test
	public void failedNodeConnectionTest() {
		RadixNetwork network = mock(RadixNetwork.class);

		RadixPeer peer = mock(RadixPeer.class);
		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(network, new RandomSelector());
		TestObserver<RadixPeer> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			Observable.just(new RadixNetworkState(ImmutableMap.of(peer, RadixClientStatus.DISCONNECTED))).concatWith(Observable.never())
		)
		.subscribe(testObserver);

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

		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(network, new GetFirstSelector());
		TestObserver<RadixPeer> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			Observable.just(new RadixNetworkState(networkStateMap)).concatWith(Observable.never())
		)
		.subscribe(testObserver);

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

		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(network, new GetFirstSelector());
		TestObserver<RadixPeer> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			networkState
		)
		.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertValue(goodPeer);
	}
}