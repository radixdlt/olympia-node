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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkDroppers;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;

import java.time.Duration;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Drops all epoch responses from the first node to send the epoch response (effectively a down node).
 * Tests to make sure that epoch changes are still smooth even with an epoch dropper.
 */
public class OneNodeNeverSendEpochResponseTest {
	private static final int numNodes = 10;
	private static final int minValidators = 4; // need at least f=1 for this test

	private final Builder bftTestBuilder = SimulationTest.builder()
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed(),
			NetworkDroppers.oneNodePerEpochResponseDropped()
		)
		.pacemakerTimeout(1000)
		.numNodes(numNodes, 4)
		.ledgerAndEpochs(View.of(4), randomEpochToNodesMapper())
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(5, TimeUnit.SECONDS),
			ConsensusMonitors.timestampChecker(Duration.ofSeconds(2)),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered()
		);

	private static Function<Long, IntStream> randomEpochToNodesMapper() {
		return epoch -> {
			final var indices = IntStream.range(0, numNodes).boxed().collect(Collectors.toList());
			final var random = new Random(epoch);
			Collections.shuffle(indices, random);
			final var numValidators = minValidators + random.nextInt(numNodes - minValidators + 1);
			return indices.subList(0, numValidators).stream().mapToInt(Integer::intValue);
		};
	}

	@Test
	public void given_deterministic_randomized_validator_sets__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.build();

		final var checkResults = bftTest.run().awaitCompletion();
		assertThat(checkResults).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}
}
