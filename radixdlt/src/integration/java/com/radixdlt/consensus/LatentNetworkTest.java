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

import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Tests with networks with imperfect and randomly latent in-order communication channels.
 * These tests comprise only static configurations of exclusively correct nodes.
 */
public class LatentNetworkTest {

	/**
	 * Tests a static configuration of 4 correct nodes with randomly latent in-order communication.
	 * The intended behaviour is that all correct instances make progress and eventually align in their commits.
	 */
	@Test
	public void given_3_correct_bfts_in_latent_network__then_all_instances_should_get_same_commits_consecutive_vertices_eventually_over_1_minute() {
		BFTTest bftTest = new BFTTest(3, 1, TimeUnit.MINUTES);
		bftTest.assertSafety();
		bftTest.assertLiveness();
		bftTest.assertAllProposalsHaveDirectParents();
		bftTest.assertNoSyncExceptions();
		bftTest.assertNoTimeouts();
		bftTest.run();
	}
}
