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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_epochs_radixengine;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.integration.distributed.simulation.monitors.application.ApplicationMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import java.util.concurrent.TimeUnit;

import com.radixdlt.integration.distributed.simulation.application.RadixEngineUniqueGenerator;
import com.radixdlt.integration.distributed.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.statecomputer.RadixEngineConfig;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

public class SanityTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.addNodeModule(RadixEngineConfig.asModule(2, 50, 10, 5))
		.addGenesisModule(RadixEngineConfig.asModule(2, 50, 10, 5))
		.ledgerAndRadixEngineWithEpochHighView()
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents(),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered(),
			RadixEngineMonitors.noInvalidProposedCommands(),
			ApplicationMonitors.mempoolCommitted()
		)
		.addMempoolSubmissionsSteadyState(new RadixEngineUniqueGenerator());

	@Test
	public void sanity_tests_should_pass() {
		SimulationTest simulationTest = bftTestBuilder
			.build();

		final var checkResults = simulationTest.run().awaitCompletion();
		assertThat(checkResults).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}
}
