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

import java.util.concurrent.TimeUnit;

public class LatentDockerNetworkTest {
	@Test
	public void given_3_correct_bfts_in_latent_network__then_all_instances_should_get_same_commits_consecutive_vertices_eventually_over_1_minute() {
		final int numNodes = 3;
		try (DockerRemoteBFTNetwork network = DockerRemoteBFTNetwork.builder()
			.numNodes(numNodes)
			.build())
		{
			RemoteBFTTest test = RemoteBFTTest.builder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.assertResponsiveness()
				.assertNoRejectedProposals()
				.assertNoSyncExceptions()
				.assertNoTimeouts()
				.assertSafety()
				.build();
			test.runBlocking(10, TimeUnit.MINUTES);
		}
	}
}
