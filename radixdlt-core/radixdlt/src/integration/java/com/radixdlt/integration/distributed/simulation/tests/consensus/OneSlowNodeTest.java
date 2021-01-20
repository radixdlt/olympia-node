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
 */

package com.radixdlt.integration.distributed.simulation.tests.consensus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Simulation which tests for bft correctness if one node is significantly slower than
 * the others but is still within bounds of synchrony. Correctness depends on whether syncing is
 * enabled or not. Both cases are verified in this test.
 */
public class OneSlowNodeTest {
	private final int minLatency = 10;
	private final int maxLatency = 200;
	private final int trips = 8;
	private final int synchronousTimeout = maxLatency * trips;
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.oneSlowProposalSender(minLatency, maxLatency)
		)
		.pacemakerTimeout(synchronousTimeout)
		.checkConsensusSafety()
		.checkConsensusAllProposalsHaveDirectParents();

	/**
	 * Tests a static configuration of 3 fast, equal nodes and 1 slow node.
	 * Test should pass even with GetVertices RPC disabled
	 */
	@Test
	public void given_4_nodes_3_fast_and_1_slow_node_and_sync_disabled__then_a_timeout_wont_occur() {
		SimulationTest test = bftTestBuilder.build();
		TestResults results = test.run();
		assertThat(results.getCheckResults()).allSatisfy((name, error) -> AssertionsForClassTypes.assertThat(error).isNotPresent());
	}
}
