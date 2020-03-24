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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.AddNodeAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesErrorAction;
import com.radixdlt.client.core.network.actions.GetLivePeersResultAction;
import com.radixdlt.client.core.network.actions.GetUniverseRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseResponseAction;
import com.radixdlt.client.core.network.actions.NodeUniverseMismatch;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DiscoverNodesEpicTest {
	@Test
	public void when_seeds_return_an_error__epic_should_not_fail() {
		Observable<RadixNode> seeds = Observable.error(new RuntimeException("BAD EXCEPTION!"));
		RadixUniverseConfig universe = mock(RadixUniverseConfig.class);
		DiscoverNodesEpic discoverNodesEpic = new DiscoverNodesEpic(seeds, universe);

		ReplaySubject<RadixNodeAction> actions = ReplaySubject.create();
		Observable<RadixNetworkState> networkState = Observable.just(mock(RadixNetworkState.class));
		Observable<RadixNodeAction> output = discoverNodesEpic.epic(actions, networkState);

		TestObserver<RadixNodeAction> testObserver = TestObserver.create();
		output.subscribe(testObserver);

		actions.onNext(DiscoverMoreNodesAction.instance());
		testObserver.assertNoErrors();
		testObserver.awaitCount(1);
		testObserver.assertValue(a -> a instanceof DiscoverMoreNodesErrorAction);
	}

	@Test
	public void when_seeds_return_a_non_matching_universe__a_node_mismatch_universe_event_should_be_emitted() {
		RadixNode node = mock(RadixNode.class);
		Observable<RadixNode> seeds = Observable.just(node);
		RadixUniverseConfig universe = mock(RadixUniverseConfig.class);
		RadixUniverseConfig badUniverse = mock(RadixUniverseConfig.class);

		ReplaySubject<RadixNodeAction> actions = ReplaySubject.create();
		Observable<RadixNetworkState> networkState = Observable.just(mock(RadixNetworkState.class));

		DiscoverNodesEpic discoverNodesEpic = new DiscoverNodesEpic(seeds, universe);
		Observable<RadixNodeAction> output = discoverNodesEpic.epic(actions, networkState);

		TestObserver<RadixNodeAction> testObserver = TestObserver.create();
		output.subscribe(testObserver);

		actions.onNext(DiscoverMoreNodesAction.instance());

		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, a -> a instanceof GetUniverseRequestAction);
		actions.onNext(GetUniverseResponseAction.of(node, badUniverse));

		testObserver.awaitCount(2);
		testObserver.assertValueAt(1, a -> a instanceof NodeUniverseMismatch);
	}

	@Test
	public void when_live_peers_result_is_observed__add_node_events_should_be_emitted() {
		RadixNode node = mock(RadixNode.class);
		when(node.getPort()).thenReturn(12345);
		Observable<RadixNode> seeds = Observable.just(node);
		RadixUniverseConfig universe = mock(RadixUniverseConfig.class);

		ReplaySubject<RadixNodeAction> actions = ReplaySubject.create();
		RadixNetworkState networkState = mock(RadixNetworkState.class);
		when(networkState.getNodeStates()).thenReturn(Collections.emptyMap());
		Observable<RadixNetworkState> observableNetworkState = Observable.concat(Observable.just(networkState), Observable.never());

		DiscoverNodesEpic discoverNodesEpic = new DiscoverNodesEpic(seeds, universe);
		Observable<RadixNodeAction> output = discoverNodesEpic.epic(actions, observableNetworkState);
		TestObserver<RadixNodeAction> testObserver = TestObserver.create();
		output.subscribe(testObserver);

		NodeRunnerData data0 = mock(NodeRunnerData.class);
		when(data0.getIp()).thenReturn("1.2.3.4");
		NodeRunnerData data1 = mock(NodeRunnerData.class);
		when(data1.getIp()).thenReturn("2.3.4.5");
		List<NodeRunnerData> nodeRunnerData = Arrays.asList(data0, data1);
		actions.onNext(GetLivePeersResultAction.of(node, nodeRunnerData));

		testObserver.awaitCount(2);
		testObserver.assertValueAt(0, a -> a instanceof AddNodeAction);
		testObserver.assertValueAt(1, a -> a instanceof AddNodeAction);
	}
}