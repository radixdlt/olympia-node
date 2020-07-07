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

package com.radixdlt.consensus.simulation.tests.epochs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.SimulationTest;
import com.radixdlt.consensus.simulation.SimulationTest.Builder;
import com.radixdlt.consensus.simulation.TestInvariant.TestInvariantError;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

public class MovingWindowValidatorsTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.checkSafety("safety")
		.checkNoTimeouts("noTimeouts")
		.checkAllProposalsHaveDirectParents("directParents");

	private static Function<Long, IntStream> windowedEpochToNodesMapper(int windowSize, int totalValidatorCount) {
		return epoch -> IntStream.range(0, windowSize).map(index -> (int) (epoch + index) % totalValidatorCount);
	}

	@Test
	public void given_correct_1_node_bft_with_4_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.pacemakerTimeout(1000)
			.checkLiveness("liveness", 1000, TimeUnit.MILLISECONDS)
			.epochHighView(View.of(100))
			.epochToNodesMapper(windowedEpochToNodesMapper(1, 4))
			.checkEpochHighView("epochHighView", View.of(100))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_3_node_bft_with_4_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.pacemakerTimeout(1000)
			.checkLiveness("liveness", 1000, TimeUnit.MILLISECONDS)
			.epochHighView(View.of(100))
			.epochToNodesMapper(windowedEpochToNodesMapper(3, 4))
			.checkEpochHighView("epochHighView", View.of(100))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_100_node_bft_with_200_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(200)
			.pacemakerTimeout(2000)
			.checkLiveness("liveness", 2000, TimeUnit.MILLISECONDS) // High timeout to make Travis happy
			.epochHighView(View.of(100))
			.epochToNodesMapper(windowedEpochToNodesMapper(100, 200))
			.checkEpochHighView("epochHighView", View.of(100))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_100_node_bft_with_200_total_nodes_with_changing_epochs_per_1_view__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(200)
			.pacemakerTimeout(2000)
			.checkLiveness("liveness", 2000, TimeUnit.MILLISECONDS) // High timeout to make Travis happy
			.epochHighView(View.of(1))
			.epochToNodesMapper(windowedEpochToNodesMapper(100, 200))
			.checkEpochHighView("epochHighView", View.of(1))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}
}
