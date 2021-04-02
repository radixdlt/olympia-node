/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.SyncMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import com.radixdlt.sync.SyncConfig;
import org.junit.Test;

/**
 * Full (non-validator) node should be able to sync with another non-validator node that's connected to a validator.
 */
public class FullNodeSyncingWithAnotherFullNodeTest {

	private static final ImmutableList<Integer> VALIDATORS = ImmutableList.of(0, 1);
	private static final int NON_VALIDATOR_SYNC_NODE = 2;
	private static final int NODE_UNDER_TEST = 3;
	private static final int MAX_LEDGER_SYNC_LAG = 300;

	private final Builder testBuilder;

	public FullNodeSyncingWithAnotherFullNodeTest() {
		this.testBuilder = SimulationTest.builder()
			.numNodes(4)
			.addressBook(ImmutableMap.of(
				0, VALIDATORS,
				1, VALIDATORS,
				2, VALIDATORS,
				// node 3 only knows of a (non-validator) node 2
				NODE_UNDER_TEST, ImmutableList.of(NON_VALIDATOR_SYNC_NODE)
			))
			.networkModules(
				NetworkOrdering.inOrder(),
				NetworkLatencies.fixed(10)
			)
			.overrideWithIncorrectModule(new AbstractModule() { // TODO: remove this hack
				@Provides
				public BFTValidatorSet genesisValidatorSet(Function<Long, BFTValidatorSet> mapper) {
					return mapper.apply(0L);
				}
			})
			.pacemakerTimeout(1000)
			.ledgerAndEpochsAndSync(
				View.of(100),
				(unused) -> VALIDATORS.stream().mapToInt(i -> i),
				SyncConfig.of(1000L, 10, 500L, 10, Long.MAX_VALUE)
			)
			.addTestModules(
				ConsensusMonitors.safety(),
				ConsensusMonitors.liveness(5, TimeUnit.SECONDS),
				ConsensusMonitors.directParents(),
				LedgerMonitors.consensusToLedger(),
				LedgerMonitors.ordered(),
				ConsensusMonitors.epochCeilingView(View.of(100)),
				SyncMonitors.maxLedgerSyncLag(MAX_LEDGER_SYNC_LAG)
			);
	}

	@Test
	public void given_a_full_node_that_is_only_conencted_to_another_full_node__it_should_sync() {
		final var simulationTest = testBuilder.build();
		final var runningTest = simulationTest.run();
		final var results = runningTest.awaitCompletion();
		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());

		final var testNodeCounters = runningTest.getNetwork()
			.getSystemCounters().get(runningTest.getNetwork().getNodes().get(NODE_UNDER_TEST));

		// just to be sure that node wasn't a validator
		assertEquals(0, testNodeCounters.get(CounterType.BFT_PROPOSALS_MADE));
		assertEquals(0, testNodeCounters.get(CounterType.BFT_PROCESSED));

		final var syncNodeCounters = runningTest.getNetwork()
			.getSystemCounters().get(runningTest.getNetwork().getNodes().get(NON_VALIDATOR_SYNC_NODE));

		// make sure that the sync target node actually processed all the requests from test node
		// and test node didn't communicate directly with a validator
		assertThat(
			syncNodeCounters.get(CounterType.SYNC_REMOTE_REQUESTS_PROCESSED) - testNodeCounters.get(CounterType.SYNC_PROCESSED)
		).isBetween(-2L, 2L); // small discrepancies are fine
	}

}
