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

package com.radixdlt.consensus.simulation.tests.bft.synchronous;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.consensus.simulation.SimulationTest;
import com.radixdlt.consensus.simulation.SimulationTest.Builder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

public class OneOutOfBoundsTest {
	private final int latency = 50;
	private final int synchronousTimeout = 8 * latency;
	private final int outOfBoundsLatency = synchronousTimeout;
	// TODO: Add 1 timeout check
	private final Builder bftTestBuilder = SimulationTest.builder()
		.pacemakerTimeout(synchronousTimeout)
		.checkLiveness("liveness", 2 * synchronousTimeout, TimeUnit.MILLISECONDS)
		.checkSafety("safety");

	/**
	 * Tests a configuration of 1 out of 4 nodes out of synchrony bounds
	 */
	@Test
	public void given_1_out_of_4_nodes_out_of_synchrony_bounds() {
		SimulationTest test = bftTestBuilder
			.numNodesAndLatencies(4, latency, latency, latency, outOfBoundsLatency)
			.build();

		Map<String, Optional<TestInvariantError>> results = test.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, error) -> AssertionsForClassTypes.assertThat(error).isNotPresent());
	}
}
