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

import com.radixdlt.consensus.simulation.BFTSimulatedTest;
import com.radixdlt.consensus.simulation.BFTSimulatedTest.Builder;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

public class OneProposalDropperTest {

	private final int minLatency = 10;
	private final int maxLatency = 200;
	private final int trips = 12;
	private final int synchronousTimeout = maxLatency * trips;
	private final Builder bftTestBuilder = BFTSimulatedTest.builder()
		.numNodes(4)
		.randomLatency(minLatency, maxLatency)
		.pacemakerTimeout(synchronousTimeout)
		.addProposalDropper()
		.checkNoTimeouts("noTimeouts");

	/**
	 * Tests a configuration of 4 nodes with a dropping proposal adversary
	 * Test should fail with GetVertices RPC disabled
	 */
	@Test
	@Ignore
	public void given_get_vertices_disabled__then_test_should_fail_against_drop_proposal_adversary() {
		BFTSimulatedTest oneSlowNodeTest = bftTestBuilder
			.setGetVerticesRPCEnabled(false)
			.build();

		oneSlowNodeTest.run(1, TimeUnit.MINUTES);
	}
}
