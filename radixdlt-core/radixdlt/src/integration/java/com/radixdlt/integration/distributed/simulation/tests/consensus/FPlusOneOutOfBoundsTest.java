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
import com.radixdlt.integration.distributed.simulation.Monitor;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class FPlusOneOutOfBoundsTest {
	private final int latency = 50;
	private final int synchronousTimeout = 8 * latency;
	private final int outOfBoundsLatency = synchronousTimeout;
	private final Builder bftTestBuilder = SimulationTest.builder()
		.pacemakerTimeout(synchronousTimeout)
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(synchronousTimeout, TimeUnit.MILLISECONDS)
		);

	/**
	 * Tests a configuration of 0 out of 3 nodes out of synchrony bounds
	 */
	@Test
	public void given_0_out_of_3_nodes_out_of_synchrony_bounds() {
		SimulationTest test = bftTestBuilder
			.numNodes(3)
			.networkModules(
				NetworkOrdering.inOrder(),
				NetworkLatencies.fixed(latency)
			)
			.build();

		TestResults results = test.run();
		assertThat(results.getCheckResults())
			.allSatisfy((name, error) -> assertThat(error).isNotPresent());
	}

	/**
	 * Tests a configuration of 1 out of 3 nodes out of synchrony bounds
	 */
	@Test
	public void given_1_out_of_3_nodes_out_of_synchrony_bounds() {
		SimulationTest test = bftTestBuilder
			.numNodes(3)
			.networkModules(
				NetworkOrdering.inOrder(),
				NetworkLatencies.oneOutOfBounds(latency, outOfBoundsLatency)
			)
			.build();

		TestResults results = test.run();
		assertThat(results.getCheckResults())
			.hasEntrySatisfying(Monitor.LIVENESS, error -> assertThat(error).isPresent())
			.hasEntrySatisfying(Monitor.SAFETY, error -> assertThat(error).isNotPresent());
	}
}
