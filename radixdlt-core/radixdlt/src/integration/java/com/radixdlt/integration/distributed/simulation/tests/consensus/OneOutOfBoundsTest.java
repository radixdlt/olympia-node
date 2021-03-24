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

import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

public class OneOutOfBoundsTest {
	private static final int latency = 50;
	private static final int synchronousTimeout = 8 * latency;
	private static final int outOfBoundsLatency = synchronousTimeout;

	// TODO: Add 1 timeout check
	private final Builder bftTestBuilder = SimulationTest.builder()
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.oneOutOfBounds(latency, outOfBoundsLatency)
		)
		.pacemakerTimeout(synchronousTimeout)
		.addTestModules(
			ConsensusMonitors.safety(),
			// FIXME: Should be 2 * synchronousTimeout, and can be set back to that once message scheduling improved
			ConsensusMonitors.liveness(4 * synchronousTimeout, TimeUnit.MILLISECONDS)
		);

	/**
	 * Tests a configuration of 1 out of 4 nodes out of synchrony bounds
	 */
	@Test
	public void given_1_out_of_4_nodes_out_of_synchrony_bounds() {
		SimulationTest test = bftTestBuilder
			.numNodes(4)
			.build();

		final var runningTest = test.run();
		final var checkResults = runningTest.awaitCompletion();

		assertThat(checkResults).allSatisfy((name, error) -> AssertionsForClassTypes.assertThat(error).isNotPresent());
	}
}
