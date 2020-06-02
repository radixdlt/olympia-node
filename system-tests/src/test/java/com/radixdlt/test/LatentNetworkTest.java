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

import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

/**
 * BFT tests against static, non-byzantine networks with random latency.
 */
public class LatentNetworkTest {
	/**
	 * Gets the test builder for latent BFT network tests.
	 * @return The test builder
	 */
	static RemoteBFTTest.Builder latentTestBuilder() {
		return RemoteBFTTest.builder()
			.assertResponsiveness()
			.assertAllProposalsHaveDirectParents()
			.assertNoRejectedProposals()
			.assertNoSyncExceptions()
			.assertNoTimeouts()
			.assertSafety()
			.assertLiveness();
	}

	@Test
	@Category(Docker.class)
	public void given_3_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		try (DockerNetwork network = DockerNetwork.builder()
			.numNodes(3)
			.build())
		{
			network.startBlocking();
			RemoteBFTTest test = latentTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(1, TimeUnit.MINUTES);
		}
	}

	@Test
	@Category(Docker.class)
	public void given_4_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		try (DockerNetwork network = DockerNetwork.builder()
				.numNodes(4)
				.build())
		{
			network.startBlocking();
			RemoteBFTTest test = latentTestBuilder()
					.network(RemoteBFTNetworkBridge.of(network))
					.waitUntilResponsive()
					.startConsensusOnRun()
					.build();
			test.runBlocking(1, TimeUnit.MINUTES);
		}
	}

	@Test
	@Category(Cluster.class)
	public void given_4_correct_bfts_in_latent_cluster_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		final StaticClusterNetwork network = StaticClusterNetwork.extractFromProperty(4);
		RemoteBFTTest test = latentTestBuilder()
			.network(RemoteBFTNetworkBridge.of(network))
			.waitUntilResponsive()
			.startConsensusOnRun() // in case we're the first to access the cluster
			.build();
		test.runBlocking(1, TimeUnit.MINUTES);
	}
}
