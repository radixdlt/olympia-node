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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_localmempool;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.google.inject.AbstractModule;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.Monitor;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.application.IncrementalBytes;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Simple mempool sanity test which runs the mempool submit and commit invariant.
 */
public class MempoolSanityTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.ledgerAndMempool()
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents(),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered()
		)
		.addMempoolSubmissionsSteadyState(new IncrementalBytes())
		.addMempoolCommittedChecker();

	/**
	 * TODO: This is more of a test for mempoolSubmissionSteadyState, should move somewhere else
	 */
	@Test
	public void when_submitting_items_to_null_mempool__then_test_should_fail() {
		SimulationTest simulationTest = bftTestBuilder
			.overrideModule(new AbstractModule() {
				@Override
				protected void configure() {
					bind(Mempool.class).to(EmptyMempool.class);
				}
			})
			.build();

		final var checkResults = simulationTest.run().awaitCompletion();
		assertThat(checkResults).hasEntrySatisfying(
			Monitor.MEMPOOL_COMMITTED,
			error -> assertThat(error).isPresent()
		);
	}

	@Test
	public void when_submitting_items_to_mempool__then_they_should_get_executed() {
		SimulationTest simulationTest = bftTestBuilder
			.build();

		final var checkResults = simulationTest.run().awaitCompletion();
		assertThat(checkResults).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}
}
