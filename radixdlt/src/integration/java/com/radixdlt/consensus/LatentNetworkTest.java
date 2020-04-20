/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Tests with networks with imperfect and randomly latent in-order communication channels.
 * These tests comprise only static configurations of exclusively correct nodes.
 */
public class LatentNetworkTest {
	/**
	 * Tests a static configuration of 3 correct nodes with randomly latent in-order communication.
	 */
	@Test
	public void given_3_correct_bfts_in_latent_network__then_all_normal_sanity_checks_should_pass() {
		BFTTest bftTest = BFTTest.builder()
			.numNodes(3)
			.time(1, TimeUnit.MINUTES)
			.networkLatency(10, 160) // 6 times max latency should be less than BFTTestNetwork.TEST_PACEMAKER_TIMEOUT
			.build();
		bftTest.assertSafety();
		bftTest.assertLiveness();
		bftTest.assertAllProposalsHaveDirectParents();
		bftTest.assertNoSyncExceptions();
		bftTest.assertNoTimeouts();
		bftTest.run();
	}

	/**
	 * Tests a static configuration of 4 correct nodes with randomly latent in-order communication.
	 */
	@Test
	@Ignore("This test currently fails due to sync not being implemented and thus one node falling behind is possible.")
	public void given_4_correct_bfts_in_latent_network__then_all_normal_sanity_checks_should_pass() {
		BFTTest bftTest = BFTTest.builder()
			.numNodes(4)
			.time(10, TimeUnit.MINUTES)
			.networkLatency(10, 160) // 6 times max latency should be less than BFTTestNetwork.TEST_PACEMAKER_TIMEOUT
			.build();
		bftTest.assertSafety();
		bftTest.assertLiveness();
		bftTest.assertAllProposalsHaveDirectParents();
		bftTest.assertNoSyncExceptions();
		bftTest.assertNoTimeouts();
		bftTest.run();
	}
}
