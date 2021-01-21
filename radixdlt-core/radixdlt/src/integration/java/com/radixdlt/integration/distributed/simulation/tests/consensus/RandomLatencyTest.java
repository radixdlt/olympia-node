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

import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkDroppers;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Simulation which randomly selects (uniform distribution) latencies for each message as
 * long as its still within the bounds of synchrony, the idea being that random edge cases
 * may be found.
 */
public class RandomLatencyTest {
	// use a maxLatency of 20x the min latency since we know a round can take up to
	// atleast 6x transmission time. 20x so that we can hit these cases more often
	private final int minLatency = 10;
	private final int maxLatency = 200;
	// the minimum latency per round is determined using the network latency
	// a round can consist of 6 * max_transmission_time (MTT)
	private final int trips = 6;
	private final int synchronousTimeout = maxLatency * trips;

	private Builder bftTestBuilder = SimulationTest.builder()
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.random(minLatency, maxLatency),
			NetworkDroppers.bftSyncMessagesDropped()
		)
		.pacemakerTimeout(synchronousTimeout) // Since no syncing needed 6*MTT required
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(synchronousTimeout, TimeUnit.MILLISECONDS),
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents()
		);

	/**
	 * Tests a static configuration of 3 nodes with random, high variance in latency
	 */
	@Test
	public void given_3_correct_nodes_in_random_network_and_no_sync__then_all_synchronous_checks_should_pass() {
		SimulationTest test = bftTestBuilder
			.numNodes(3)
			.build();

		TestResults results = test.run();
		assertThat(results.getCheckResults()).allSatisfy((name, error) -> assertThat(error).isNotPresent());
	}

	/**
	 * Tests a static configuration of 4 nodes with random, high variance in latency
	 */
	@Test
	public void given_4_correct_bfts_in_random_network_and_no_sync__then_all_synchronous_checks_should_pass() {
		SimulationTest test = bftTestBuilder
			.numNodes(4)
			.build();

		TestResults results = test.run();
		assertThat(results.getCheckResults()).allSatisfy((name, error) -> assertThat(error).isNotPresent());
	}
}
