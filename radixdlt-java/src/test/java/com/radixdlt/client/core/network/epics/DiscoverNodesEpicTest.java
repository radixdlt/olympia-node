package com.radixdlt.client.core.network.epics;

import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.GetUniverseRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseResultAction;
import com.radixdlt.client.core.network.actions.NodeUniverseMismatch;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import org.junit.Test;

public class DiscoverNodesEpicTest {
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
		actions.onNext(GetUniverseResultAction.of(node, badUniverse));

		testObserver.awaitCount(2);
		testObserver.assertValueAt(1, a -> a instanceof NodeUniverseMismatch);
	}
}