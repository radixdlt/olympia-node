package com.radix.regression;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.bootstrap.NodeFinder;
import com.radixdlt.client.core.network.epics.DiscoverNodesEpic;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class BootstrapTest {
	@Test
	public void when_bootstrap_with_down_node_finder__api_should_not_crash() throws InterruptedException {
		NodeFinder nodeFinder = new NodeFinder("https://notexist.radixdlt.com/bad-url", 443);
		RadixApplicationAPI api = RadixApplicationAPI.create(new BootstrapConfig() {
			@Override
			public RadixUniverseConfig getConfig() {
				return RadixEnv.getBootstrapConfig().getConfig();
			}

			@Override
			public List<RadixNetworkEpic> getDiscoveryEpics() {
				return Collections.singletonList(new DiscoverNodesEpic(nodeFinder.getSeed().toObservable(), RadixEnv.getBootstrapConfig().getConfig()));
			}

			@Override
			public Set<RadixNode> getInitialNetwork() {
				return ImmutableSet.of();
			}
		}, RadixIdentities.createNew());

		api.pull();
		TestObserver<RadixNetworkState> testObserver = TestObserver.create();
		api.getNetworkState().subscribe(testObserver);
		testObserver.await(5, TimeUnit.SECONDS);
		testObserver.assertNoErrors();
		testObserver.assertNotComplete();
	}
}
