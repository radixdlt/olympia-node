/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.test;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assume;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class LatentClusterNetworkTest {
	private static final String CLUSTER_NODE_URLS_PROPERTY = "clusterNodeUrls";
	private static final Logger logger = LogManager.getLogger();

	@Test
	public void given_3_correct_bfts_in_latent_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		ImmutableList<String> clusterNodeUrls = getClusterNodeUrls(3);
		logger.info("using cluster of {} nodes: {}", clusterNodeUrls.size(), clusterNodeUrls);

		final StaticRemoteBFTNetwork network = StaticRemoteBFTNetwork.from(clusterNodeUrls);
		RemoteBFTTest test = BFTNetworkTests.latentTestBuilder()
			.network(RemoteBFTNetworkBridge.of(network))
			.waitUntilResponsive()
			.startConsensusOnRun() // in case we're the first to run it
			.build();
		test.runBlocking(1, TimeUnit.MINUTES);
	}

	/**
	 * Extracts cluster node URLs out of the CLUSTER_NODE_URLS_PROPERTY.
	 * The test will be ignored if the number of non-empty URLs does not match the expected count.
	 *
	 * @param expectedNumNodes The expected number of node URLs
	 * @return A list of non-empty node urls as set in CLUSTER_NODE_URLS_PROPERTY
	 */
	private static ImmutableList<String> getClusterNodeUrls(int expectedNumNodes) {
		ImmutableList<String> clusterNodeUrls = Arrays.stream(System.getProperty(CLUSTER_NODE_URLS_PROPERTY, "")
			.split(","))
			.filter(url -> !url.isEmpty())
			.collect(ImmutableList.toImmutableList());
		Assume.assumeTrue(String.format(
			"system property %s set with %d comma-separated node urls", CLUSTER_NODE_URLS_PROPERTY, expectedNumNodes),
			expectedNumNodes == clusterNodeUrls.size()
		);
		return clusterNodeUrls;
	}
}
