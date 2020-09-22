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

import com.google.inject.AbstractModule;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRequestSender;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import org.junit.Test;

/**
 * Simulation with a communication adversary which drops a random proposal message in every
 * round.
 */
public class OneProposalPerViewDropperTest {
	private final int minLatency = 10;
	private final int maxLatency = 200;
	private final int trips = 20;
	private final int synchronousTimeout = maxLatency * trips;
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.randomLatency(minLatency, maxLatency)
		.pacemakerTimeout(synchronousTimeout)
		.addOneProposalPerViewDropper()
		.checkConsensusSafety("safety")
		.checkConsensusNoTimeouts("noTimeouts");

	/**
	 * Tests a configuration of 4 nodes with a dropping proposal adversary
	 * Test should fail with GetVertices RPC disabled
	 */
	@Test
	public void given_incorrect_module_where_vertex_sync_is_disabled__then_test_should_fail_against_drop_proposal_adversary() {
		SimulationTest test = bftTestBuilder
			.overrideWithIncorrectModule(new AbstractModule() {
				@Override
				protected void configure() {
					bind(SyncVerticesRequestSender.class).toInstance((hash, node, count, opaque) -> { });
				}
			})
			.build();

		TestResults results = test.run();
		assertThat(results.getCheckResults()).hasEntrySatisfying("noTimeouts", error -> assertThat(error).isPresent());
	}

	/**
	 * Tests a configuration of 4 nodes with a dropping proposal adversary
	 * Test should fail with GetVertices RPC disabled
	 */
	@Test
	public void given_get_vertices_enabled__then_test_should_succeed_against_drop_proposal_adversary() {
		SimulationTest test = bftTestBuilder.build();
		TestResults results = test.run();
		assertThat(results.getCheckResults()).allSatisfy((name, error) -> assertThat(error).isNotPresent());
	}
}
