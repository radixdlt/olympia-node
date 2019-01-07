package com.radixdlt.client.core.network.epics;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.atoms.Shards;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeState;
import com.radixdlt.client.core.network.selector.GetFirstSelector;
import com.radixdlt.client.core.network.selector.RandomSelector;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FindANodeMiniEpicTest {
	private RadixNodeState mockedNodeState(WebSocketStatus status, long lowShard, long highShard) {
		RadixNodeState nodeState = mock(RadixNodeState.class);
		when(nodeState.getStatus()).thenReturn(status);
		when(nodeState.getShards()).thenReturn(Optional.of(Shards.range(lowShard, highShard)));

		return nodeState;
	}

	@Test
	public void testValidClient() {
		RadixNode node = mock(RadixNode.class);
		WebSocketClient ws = mock(WebSocketClient.class);
		when(ws.getMessages()).thenReturn(Observable.never());
		when(ws.sendMessage(any())).thenReturn(true);

		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(new GetFirstSelector());
		TestObserver<NodeUpdate> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			Observable.just(RadixNetworkState.of(node, mockedNodeState(WebSocketStatus.CONNECTED, 1, 1)))
		)
		.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(u -> u.getNode().equals(node));
		testObserver.assertComplete();
	}

	@Test
	public void failedNodeConnectionTest() {
		RadixNode node = mock(RadixNode.class);
		FindANodeMiniEpic findANodeFunction = new FindANodeMiniEpic(new RandomSelector());

		RadixNodeState nodeState = mock(RadixNodeState.class);
		when(nodeState.getStatus()).thenReturn(WebSocketStatus.DISCONNECTED);
		when(nodeState.getShards()).thenReturn(Optional.of(Shards.range(1, 1)));

		TestObserver<NodeUpdate> testObserver = TestObserver.create();
		findANodeFunction.apply(
			Collections.singleton(1L),
			Observable.just(RadixNetworkState.of(node, nodeState)).concatWith(Observable.never())
		)
		.subscribe(testObserver);

		testObserver.awaitTerminalEvent(50, TimeUnit.MILLISECONDS);
		testObserver.assertNotComplete();
	}

	@Test
	public void dontConnectToAllNodesTest() {
		RadixNode connectedPeer = mock(RadixNode.class);

		Map<RadixNode, RadixNodeState> networkStateMap = IntStream.range(0, 100).boxed().collect(Collectors.toMap(
			i -> {
				if (i == 0) {
					return connectedPeer;
				}

				RadixNode peer = mock(RadixNode.class);
				return peer;
			},
			i -> mockedNodeState(i == 0 ? WebSocketStatus.CONNECTED : WebSocketStatus.DISCONNECTED, 1, 1)
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
			badPeer, mockedNodeState(WebSocketStatus.DISCONNECTED, 1, 1),
			goodPeer, mockedNodeState(WebSocketStatus.DISCONNECTED, 1, 1)
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
					badPeer, mockedNodeState(WebSocketStatus.FAILED, 1, 1),
					goodPeer, mockedNodeState(WebSocketStatus.DISCONNECTED, 1, 1)
				)));
			} else {
				networkState.onNext(new RadixNetworkState(ImmutableMap.of(
					badPeer, mockedNodeState(WebSocketStatus.FAILED, 1, 1),
					goodPeer, mockedNodeState(WebSocketStatus.CONNECTED, 1, 1)
				)));
			}
		})
		.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertValueAt(0, u -> u.getType().equals(NodeUpdateType.WEBSOCKET_CONNECT) && u.getNode().equals(badPeer));
		testObserver.assertValueAt(1, u -> u.getType().equals(NodeUpdateType.WEBSOCKET_CONNECT) && u.getNode().equals(goodPeer));
		testObserver.assertValueAt(2, u -> u.getType().equals(NodeUpdateType.SELECT_NODE) && u.getNode().equals(goodPeer));
	}
}