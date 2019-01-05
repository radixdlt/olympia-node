package com.radixdlt.client.core.network;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.ledger.selector.GetFirstSelector;
import com.radixdlt.client.core.ledger.selector.RandomSelector;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import com.radixdlt.client.core.network.epics.FindANodeMiniEpic;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FindANodeMiniEpicTest {
	@Test
	public void testValidClient() {
		RadixNode peer = mock(RadixNode.class);
		WebSocketClient ws = mock(WebSocketClient.class);
		when(ws.getMessages()).thenReturn(Observable.never());
		when(ws.sendMessage(any())).thenReturn(true);

		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(new GetFirstSelector());
		TestObserver<NodeUpdate> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			Observable.just(new RadixNetworkState(ImmutableMap.of(peer, RadixClientStatus.CONNECTED)))
		)
		.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(u -> u.getNode().equals(peer));
		testObserver.assertComplete();
	}

	@Test
	public void failedNodeConnectionTest() {
		RadixNode peer = mock(RadixNode.class);
		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(new RandomSelector());
		TestObserver<NodeUpdate> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			Observable.just(new RadixNetworkState(ImmutableMap.of(peer, RadixClientStatus.DISCONNECTED))).concatWith(Observable.never())
		)
		.subscribe(testObserver);

		testObserver.awaitTerminalEvent(50, TimeUnit.MILLISECONDS);
		testObserver.assertNotComplete();
	}

	@Test
	public void dontConnectToAllNodesTest() {
		RadixNode connectedPeer = mock(RadixNode.class);

		Map<RadixNode, RadixClientStatus> networkStateMap = IntStream.range(0, 100).boxed().collect(Collectors.toMap(
			i -> {
				if (i == 0) {
					return connectedPeer;
				}

				RadixNode peer = mock(RadixNode.class);
				return peer;
			},
			i -> i == 0 ? RadixClientStatus.CONNECTED : RadixClientStatus.DISCONNECTED
		));

		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(new GetFirstSelector());
		TestObserver<NodeUpdate> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			Observable.just(new RadixNetworkState(networkStateMap)).concatWith(Observable.never())
		)
		.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(u -> u.getNode().equals(connectedPeer));
	}

	@Test
	public void whenFirstNodeFailsThenSecondNodeShouldConnect() {
		RadixNode badPeer = mock(RadixNode.class);
		RadixNode goodPeer = mock(RadixNode.class);

		ReplaySubject<RadixNetworkState> networkState = ReplaySubject.create();

		networkState.onNext(new RadixNetworkState(ImmutableMap.of(
			badPeer, RadixClientStatus.DISCONNECTED,
			goodPeer, RadixClientStatus.DISCONNECTED
		)));

		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(new GetFirstSelector());
		TestObserver<NodeUpdate> testObserver = TestObserver.create();

		findANodeFunction.apply(
			Collections.singleton(1L),
			networkState
		)
		.doOnNext(i -> {
			if (i.getNode().equals(badPeer)) {
				networkState.onNext(new RadixNetworkState(ImmutableMap.of(
					badPeer, RadixClientStatus.FAILED,
					goodPeer, RadixClientStatus.DISCONNECTED
				)));
			} else {
				networkState.onNext(new RadixNetworkState(ImmutableMap.of(
					badPeer, RadixClientStatus.FAILED,
					goodPeer, RadixClientStatus.CONNECTED
				)));
			}
		})
		.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertValueAt(0, u -> u.getType().equals(NodeUpdateType.START_CONNECT) && u.getNode().equals(badPeer));
		testObserver.assertValueAt(1, u -> u.getType().equals(NodeUpdateType.START_CONNECT) && u.getNode().equals(goodPeer));
		testObserver.assertValueAt(2, u -> u.getType().equals(NodeUpdateType.SELECT_NODE) && u.getNode().equals(goodPeer));
	}
}