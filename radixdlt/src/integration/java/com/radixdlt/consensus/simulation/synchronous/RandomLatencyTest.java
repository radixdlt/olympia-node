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
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Simulation which randomly selects (uniform distribution) latencies for each message as
 * long as its still within the bounds of synchrony, the idea being that random edge cases
 * may be found.
 */
public class RandomLatencyTest {
	/**
	 * Tests a static configuration of 3 correct nodes with random, high variance in latency
	 */
	@Test
	public void given_3_correct_nodes_in_random_network_and_no_sync__then_all_synchronous_checks_should_pass() {
		// use a maxLatency of 20x the min latency since we know a round can take up to
		// atleast 6x transmission time. 20x so that we can hit these cases more often
		final int minLatency = 10;
		final int maxLatency = 200;
		// the minimum latency per round is determined using the network latency
		// a round can consist of 6 * max_transmission_time (MTT)
		final int trips = 6;
		final int synchronousTimeout = maxLatency * trips;

		BFTTest bftTest = BFTTest.builder()
			.numNodes(3)
			.randomLatency(minLatency, maxLatency)
			.disableSync(true)
			.pacemakerTimeout(synchronousTimeout) // Since no syncing needed 6*MTT required
			.checkLiveness(synchronousTimeout, TimeUnit.MILLISECONDS)
			.checkSafety()
			.checkAllProposalsHaveDirectParents()
			.checkNoTimeouts()
			.build();

		bftTest.run(1, TimeUnit.MINUTES);
	}

	/**
	 * Tests a static configuration of 4 randomly latent nodes.
	 * With syncing disabled, probabilistically speaking this should eventually timeout
	 * due to the case of a node randomly falling behind.
	 */
	@Test
	public void given_4_correct_bfts_in_random_network_and_no_sync__then_network_should_eventually_timeout() {
		final int minLatency = 10;
		final int maxLatency = 200;
		final int trips = 8;
		final int synchronousTimeout = maxLatency * trips;

		BFTTest bftTest = BFTTest.builder()
			.numNodes(4)
			.pacemakerTimeout(synchronousTimeout)
			.randomLatency(minLatency, maxLatency)
			.disableSync(true)
			.checkNoTimeouts()
			.build();
		assertThatThrownBy(() -> bftTest.run(10, TimeUnit.MINUTES))
			.isInstanceOf(AssertionError.class);
	}
}
