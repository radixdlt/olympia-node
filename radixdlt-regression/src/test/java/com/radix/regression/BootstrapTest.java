/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
				return Collections.singletonList(
					new DiscoverNodesEpic(nodeFinder.getSeed().toObservable(), RadixEnv.getBootstrapConfig().getConfig())
				);
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
