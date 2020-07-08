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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Simulation test which has the same latency for every message
 */
public class UniformLatencyTest {
	/**
	 * Sanity test check for a perfect network. 4 is the size used because it is
	 * the smallest network size where quorum size (3) != network size. The sanity checks
	 * done are:
	 * 1. Committed vertices are the same across nodes
	 * 2. The size of vertex store does not increase for any node
	 * 3. A timeout never occurs for any node
	 * 4. Every proposal has a direct parent
	 */
	@Test
	public void given_4_correct_bfts__then_should_pass_sanity_tests_over_1_minute() {
		SimulationTest bftTest = SimulationTest.builder()
			.numNodes(4)
			.checkSafety("safety")
			.checkLiveness("liveness")
			.checkNoTimeouts("noTimeouts")
			.checkAllProposalsHaveDirectParents("directParents")
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}
}
