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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_epochs;

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.Test;

public class MovingWindowValidatorsTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents(),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered()
		);

	private static Function<Long, IntStream> windowedEpochToNodesMapper(int windowSize, int totalValidatorCount) {
		return epoch -> IntStream.range(0, windowSize).map(index -> (int) (epoch + index) % totalValidatorCount);
	}

	@Test
	public void given_correct_1_node_bft_with_4_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(4, 2)
			.ledgerAndEpochs(View.of(100), windowedEpochToNodesMapper(1, 4))
			.pacemakerTimeout(5000)
			.addTestModules(
				ConsensusMonitors.liveness(5, TimeUnit.SECONDS),
				ConsensusMonitors.timestampChecker(),
				ConsensusMonitors.epochCeilingView(View.of(100))
			)
			.build();
		final var checkResults = bftTest.run().awaitCompletion();
		assertThat(checkResults).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_3_node_bft_with_4_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(4, 2)
			.ledgerAndEpochs(View.of(100), windowedEpochToNodesMapper(3, 4))
			.pacemakerTimeout(1000)
			.addTestModules(
				ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
				ConsensusMonitors.timestampChecker(),
				ConsensusMonitors.epochCeilingView(View.of(100))
			)
			.build();
		final var checkResults = bftTest.run().awaitCompletion();
		assertThat(checkResults).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_25_node_bft_with_50_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(100, 2)
			.ledgerAndEpochs(View.of(100), windowedEpochToNodesMapper(25, 50))
			.pacemakerTimeout(5000)
			.addTestModules(
				ConsensusMonitors.liveness(5, TimeUnit.SECONDS), // High timeout to make Travis happy
				ConsensusMonitors.timestampChecker(),
				ConsensusMonitors.epochCeilingView(View.of(100))
			)
			.build();

		final var checkResults = bftTest.run().awaitCompletion();
		assertThat(checkResults).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_25_node_bft_with_50_total_nodes_with_changing_epochs_per_1_view__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(100, 2)
			.ledgerAndEpochs(View.of(1), windowedEpochToNodesMapper(25, 50))
			.pacemakerTimeout(5000)
			.addTestModules(
				ConsensusMonitors.epochCeilingView(View.of(1)),
				ConsensusMonitors.liveness(5, TimeUnit.SECONDS) // High timeout to make Travis happy
			)
			.build();

		final var checkResults = bftTest.run().awaitCompletion();
		assertThat(checkResults).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}
}
