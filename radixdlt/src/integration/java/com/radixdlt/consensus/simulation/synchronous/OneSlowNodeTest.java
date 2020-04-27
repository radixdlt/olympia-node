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

package com.radixdlt.consensus.simulation.synchronous;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.radixdlt.consensus.simulation.BFTTest;
import com.radixdlt.consensus.simulation.BFTTest.Builder;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Simulation which tests for synchronous correctness if one node is significantly slower than
 * the others but is still within bounds of synchrony. Correctness depends on whether syncing is
 * enabled or not. Both cases are verified in this test.
 */
public class OneSlowNodeTest {
	private final int minLatency = 10;
	private final int maxLatency = 200;
	private final int trips = 8;
	private final int synchronousTimeout = maxLatency * trips;
	private final Builder bftTestBuilder = BFTTest.builder()
		.numNodesAndLatencies(4, minLatency, minLatency, minLatency, maxLatency)
		.pacemakerTimeout(synchronousTimeout)
		.checkNoTimeouts();

	/**
	 * Tests a static configuration of 3 fast, equal nodes and 1 slow node.
	 * With syncing disabled, the slow node will always fall behind by at most a single
	 * quorum and issue a local timeout across all nodes.
	 */
	@Test
	public void given_4_nodes_3_fast_and_1_slow_node_and_sync_disabled__then_a_timeout_will_occur() {
		BFTTest syncDisabledTest = bftTestBuilder
			.disableSync(true)
			.build();
		assertThatThrownBy(() -> syncDisabledTest.run(1, TimeUnit.MINUTES)).isInstanceOf(AssertionError.class);
	}

	/**
	 * Tests a static configuration of 3 fast, equal nodes and 1 slow node.
	 * With syncing enabled, because all nodes are within synchronous bounds
	 * there should be no timeout.
	 */
	@Test
	public void given_4_nodes_3_fast_and_1_slow_node_and_sync_enabled__then_a_timeout_wont_occur() {
		BFTTest syncEnabledTest = bftTestBuilder
			.disableSync(false)
			.checkSyncsHaveOccurred(20, TimeUnit.SECONDS)
			.build();
		syncEnabledTest.run(1, TimeUnit.MINUTES);
	}

}
