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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

public class RandomValidatorsTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(200)
		.checkSafety("safety")
		.checkLiveness("liveness", 1000, TimeUnit.MILLISECONDS)
		.checkNoTimeouts("noTimeouts")
		.checkAllProposalsHaveDirectParents("directParents");

	private static Function<Long, IntStream> randomEpochToNodesMapper(int totalValidatorCount) {
		return epoch -> {
			List<Integer> indices = IntStream.range(0, totalValidatorCount).boxed().collect(Collectors.toList());
			Random random = new Random(epoch);
			for (long i = 0; i < epoch; i++) {
				random.nextInt(totalValidatorCount);
			}
			return IntStream.range(0, random.nextInt(totalValidatorCount) + 1)
				.map(i -> indices.remove(random.nextInt(indices.size())));
		};
	}

	@Test
	public void given_correct_100_node_bft_with_200_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.pacemakerTimeout(1000)
			.epochHighView(View.of(100))
			.epochToNodesMapper(randomEpochToNodesMapper(200))
			.checkEpochHighView("epochHighView", View.of(100))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_100_node_bft_with_200_total_nodes_with_changing_epochs_per_1_view__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.pacemakerTimeout(1000)
			.epochHighView(View.of(1))
			.epochToNodesMapper(randomEpochToNodesMapper(200))
			.checkEpochHighView("epochHighView", View.of(1))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

}
