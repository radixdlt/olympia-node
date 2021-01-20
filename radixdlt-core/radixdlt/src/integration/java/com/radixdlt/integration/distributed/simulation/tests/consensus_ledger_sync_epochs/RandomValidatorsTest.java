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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_sync_epochs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.Condition;
import org.junit.Test;

public class RandomValidatorsTest {
	private static final int numNodes = 10;

	private final Builder bftTestBuilder = SimulationTest.builder()
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.ledgerAndEpochsAndSync(View.of(3), goodRandomEpochToNodesMapper(), 50) // TODO: investigate why this fails with View.of(10)
		.pacemakerTimeout(5000)
		.numNodes(numNodes, 2)
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(5, TimeUnit.SECONDS),
			ConsensusMonitors.vertexRequestRate(50), // Conservative check
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents(),
			ConsensusMonitors.epochCeilingView(View.of(100)),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered()
		);

	private static Function<Long, IntStream> randomEpochToNodesMapper(Function<Long, Random> randomSupplier) {
		return epoch -> {
			List<Integer> indices = IntStream.range(0, numNodes).boxed().collect(Collectors.toList());
			Random random = randomSupplier.apply(epoch);
			for (long i = 0; i < epoch; i++) {
				random.nextInt(numNodes);
			}
			return IntStream.range(0, random.nextInt(numNodes) + 1)
				.map(i -> indices.remove(random.nextInt(indices.size())));
		};
	}

	private static Function<Long, IntStream> goodRandomEpochToNodesMapper() {
		return randomEpochToNodesMapper(Random::new);
	}

	@Test
	public void given_deterministic_randomized_validator_sets__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.build();

		TestResults results = bftTest.run();
		assertThat(results.getCheckResults()).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	@Test
	public void given_deterministic_randomized_validator_sets_with_incorrect_single_epoch_syncing__then_should_fail() {
		SimulationTest bftTest = bftTestBuilder
			.overrideWithIncorrectModule(new AbstractModule() {
				@Override
				public void configure() {
					bind(new TypeLiteral<RemoteEventProcessor<DtoCommandsAndProof>>() { })
						.to(RemoteSyncResponseValidatorSetVerifier.class);
				}
			})
			.build();

		TestResults results = bftTest.run();
		assertThat(results.getCheckResults()).hasValueSatisfying(new Condition<>(Optional::isPresent, "Error exists"));
	}
}
