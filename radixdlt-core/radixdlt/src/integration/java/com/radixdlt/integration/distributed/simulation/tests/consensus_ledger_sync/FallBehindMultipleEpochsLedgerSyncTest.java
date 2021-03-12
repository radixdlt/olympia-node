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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_sync;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.sync.SyncConfig;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A node that falls behind multiple epochs should be able to successfully sync its ledger
 * and keep up once synced.
 */
public class FallBehindMultipleEpochsLedgerSyncTest {
	private static final int NODE_UNDER_TEST_INDEX = 2;
	private static final long SYNC_DELAY = 5000L;

	private final Builder testBuilder;

	public FallBehindMultipleEpochsLedgerSyncTest() {
		this.testBuilder = SimulationTest.builder()
			.numNodes(3)
			.networkModules(
				NetworkOrdering.inOrder(),
				NetworkLatencies.fixed(10)
			)
			.overrideWithIncorrectModule(new AbstractModule() {
				@Provides
				public BFTValidatorSet genesisValidatorSet(Function<Long, BFTValidatorSet> mapper) {
					return mapper.apply(0L);
				}
			})
			.pacemakerTimeout(1000)
			.ledgerAndEpochsAndSync(View.of(10), (unused) -> IntStream.of(0, 1), SyncConfig.of(50L, 10, 50L))
			.addTestModules(
				ConsensusMonitors.safety(),
				ConsensusMonitors.liveness(5, TimeUnit.SECONDS),
				ConsensusMonitors.directParents(),
				LedgerMonitors.consensusToLedger(),
				LedgerMonitors.ordered(),
				ConsensusMonitors.epochCeilingView(View.of(10))
			);
	}

	@Test
	public void given_a_node_that_falls_behind_multiple_epochs__it_should_sync_up() {
		final var simulationTest = testBuilder.build();

		final var runningTest = simulationTest.run(
			Duration.ofSeconds(15),
			ImmutableMap.of(NODE_UNDER_TEST_INDEX, ImmutableSet.of("sync"))
		);

		Executors.newSingleThreadScheduledExecutor()
			.schedule(() -> runningTest.getNetwork().runModule(NODE_UNDER_TEST_INDEX, "sync"), SYNC_DELAY, TimeUnit.MILLISECONDS);

		final var results = runningTest.awaitCompletion();

		final var nodeCounters = runningTest.getNetwork()
			.getSystemCounters().get(runningTest.getNetwork().getNodes().get(NODE_UNDER_TEST_INDEX));

		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());

		// node must be synced up to some state after the first epoch
		// and must not fall behind too much
		assertTrue(nodeCounters.get(CounterType.SYNC_TARGET_CURRENT_DIFF) < 200);
		assertTrue(nodeCounters.get(CounterType.SYNC_PROCESSED) > 200);
		assertTrue(nodeCounters.get(CounterType.LEDGER_STATE_VERSION) > 200);
		// just to be sure that node wasn't a validator
		assertEquals(0, nodeCounters.get(CounterType.BFT_PROPOSALS_MADE));
		assertEquals(0, nodeCounters.get(CounterType.BFT_PROCESSED));
	}
}
