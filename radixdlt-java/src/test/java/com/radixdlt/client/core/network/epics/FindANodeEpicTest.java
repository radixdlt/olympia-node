/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network.epics;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeState;
import com.radixdlt.client.core.network.actions.CloseWebSocketAction;
import com.radixdlt.client.core.network.actions.ConnectWebSocketAction;
import com.radixdlt.client.core.network.actions.FindANodeRequestAction;
import com.radixdlt.client.core.network.actions.FindANodeResultAction;
import com.radixdlt.client.core.network.selector.GetFirstSelector;
import com.radixdlt.client.core.network.selector.RandomSelector;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import java.util.Map;
import org.junit.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FindANodeEpicTest {
	private RadixNodeState mockedNodeState(WebSocketStatus status) {
		RadixNodeState nodeState = mock(RadixNodeState.class);
		when(nodeState.getStatus()).thenReturn(status);
		return nodeState;
	}

	@Test
	public void testValidClient() {
		RadixNode node = mock(RadixNode.class);
		WebSocketClient ws = mock(WebSocketClient.class);
		when(ws.sendMessage(any())).thenReturn(true);

		FindANodeRequestAction request = mock(FindANodeRequestAction.class);

		FindANodeEpic findANodeFunction = new FindANodeEpic(new GetFirstSelector());
		TestObserver<RadixNodeAction> testObserver = TestObserver.create();
		findANodeFunction.epic(
			Observable.<RadixNodeAction>just(request).concatWith(Observable.never()),
			Observable.just(RadixNetworkState.of(node, mockedNodeState(WebSocketStatus.CONNECTED)))
		)
		.subscribe(testObserver);

		testObserver.awaitCount(1);
		testObserver.assertValue(u -> u.getNode().equals(node));
	}

	@Test
	public void disconnectedNodeConnectionTest() {
		RadixNode node = mock(RadixNode.class);
		FindANodeEpic findANodeEpic = new FindANodeEpic(new RandomSelector());

		RadixNodeState nodeState = mock(RadixNodeState.class);
		when(nodeState.getStatus()).thenReturn(WebSocketStatus.DISCONNECTED);

		FindANodeRequestAction request = mock(FindANodeRequestAction.class);

		TestObserver<RadixNodeAction> testObserver = TestObserver.create();
		findANodeEpic.epic(
			Observable.<RadixNodeAction>just(request).concatWith(Observable.never()),
			Observable.just(RadixNetworkState.of(node, nodeState)).concatWith(Observable.never())
		)
		.subscribe(testObserver);

		testObserver.awaitCount(1);
		testObserver.assertValue(u -> u instanceof ConnectWebSocketAction);
		testObserver.assertValue(u -> u.getNode().equals(node));
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
			i -> mockedNodeState(i == 0 ? WebSocketStatus.CONNECTED : WebSocketStatus.DISCONNECTED)
		));

		FindANodeRequestAction request = mock(FindANodeRequestAction.class);

		FindANodeEpic findANodeFunction = new FindANodeEpic(new GetFirstSelector());
		TestObserver<RadixNodeAction> testObserver = TestObserver.create();
		findANodeFunction.epic(
			Observable.<RadixNodeAction>just(request).concatWith(Observable.never()),
			Observable.just(new RadixNetworkState(networkStateMap)).concatWith(Observable.never())
		)
		.subscribe(testObserver);

		testObserver.assertValue(u -> u.getNode().equals(connectedPeer));
		testObserver.assertValue(u -> ((FindANodeResultAction) u).getRequest().equals(request));
	}

	@Test
	public void when_first_node_takes_too_long__then_second_node_should_connect() {
		RadixNode badPeer = mock(RadixNode.class);
		RadixNode goodPeer = mock(RadixNode.class);

		ReplaySubject<RadixNetworkState> networkState = ReplaySubject.create();


		networkState.onNext(new RadixNetworkState(ImmutableMap.of(
			badPeer, mockedNodeState(WebSocketStatus.DISCONNECTED),
			goodPeer, mockedNodeState(WebSocketStatus.DISCONNECTED)
		)));

		FindANodeEpic findANodeEpic = new FindANodeEpic(new GetFirstSelector());
		TestObserver<RadixNodeAction> testObserver = TestObserver.create();

		FindANodeRequestAction request = mock(FindANodeRequestAction.class);

		findANodeEpic.epic(
			Observable.<RadixNodeAction>just(request).concatWith(Observable.never()),
			networkState
		)
			.doOnNext(i -> {
				if (i.getNode().equals(badPeer)) {
					networkState.onNext(new RadixNetworkState(ImmutableMap.of(
						badPeer, mockedNodeState(WebSocketStatus.CONNECTING),
						goodPeer, mockedNodeState(WebSocketStatus.DISCONNECTED)
					)));
				} else {
					networkState.onNext(new RadixNetworkState(ImmutableMap.of(
						badPeer, mockedNodeState(WebSocketStatus.CONNECTING),
						goodPeer, mockedNodeState(WebSocketStatus.CONNECTED)
					)));
				}
			})
			.subscribe(testObserver);

		testObserver.awaitCount(4);
		testObserver.assertValueAt(0, u -> u instanceof ConnectWebSocketAction && u.getNode().equals(badPeer));
		testObserver.assertValueAt(1, u -> u instanceof ConnectWebSocketAction && u.getNode().equals(goodPeer));
		testObserver.assertValueAt(2, u -> u instanceof CloseWebSocketAction && u.getNode().equals(badPeer));
		testObserver.assertValueAt(3, u -> ((FindANodeResultAction) u).getRequest().equals(request) && u.getNode().equals(goodPeer));
	}

	@Test
	public void when_first_node_fails__then_second_node_should_connect() {
		RadixNode badPeer = mock(RadixNode.class);
		RadixNode goodPeer = mock(RadixNode.class);

		ReplaySubject<RadixNetworkState> networkState = ReplaySubject.create();


		networkState.onNext(new RadixNetworkState(ImmutableMap.of(
			badPeer, mockedNodeState(WebSocketStatus.DISCONNECTED),
			goodPeer, mockedNodeState(WebSocketStatus.DISCONNECTED)
		)));

		FindANodeEpic findANodeEpic = new FindANodeEpic(new GetFirstSelector());
		TestObserver<RadixNodeAction> testObserver = TestObserver.create();

		FindANodeRequestAction request = mock(FindANodeRequestAction.class);

		findANodeEpic.epic(
			Observable.<RadixNodeAction>just(request).concatWith(Observable.never()),
			networkState
		)
		.doOnNext(i -> {
			if (i.getNode().equals(badPeer)) {
				networkState.onNext(new RadixNetworkState(ImmutableMap.of(
					badPeer, mockedNodeState(WebSocketStatus.FAILED),
					goodPeer, mockedNodeState(WebSocketStatus.DISCONNECTED)
				)));
			} else {
				networkState.onNext(new RadixNetworkState(ImmutableMap.of(
					badPeer, mockedNodeState(WebSocketStatus.FAILED),
					goodPeer, mockedNodeState(WebSocketStatus.CONNECTED)
				)));
			}
		})
		.subscribe(testObserver);

		testObserver.awaitCount(4);
		testObserver.assertValueAt(0, u -> u instanceof ConnectWebSocketAction && u.getNode().equals(badPeer));
		testObserver.assertValueAt(1, u -> u instanceof ConnectWebSocketAction && u.getNode().equals(goodPeer));
		testObserver.assertValueAt(2, u -> u instanceof CloseWebSocketAction && u.getNode().equals(badPeer));
		testObserver.assertValueAt(3, u -> ((FindANodeResultAction) u).getRequest().equals(request) && u.getNode().equals(goodPeer));
	}
}