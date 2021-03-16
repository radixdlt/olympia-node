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

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.ApplicationMonitors;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import java.util.concurrent.TimeUnit;

import com.radixdlt.integration.distributed.simulation.application.NodeValidatorRegistrator;
import org.junit.Test;

/**
 * Slowly registers more and more validators to the network
 */
public class IncreasingValidatorsTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.numNodes(50, 2, 50) // Can't be 1 otherwise epochs move too fast, TODO: Fix with mempool-aware pacemaker
		.ledgerAndRadixEngineWithEpochHighView(View.of(10))
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents(),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered(),
			ApplicationMonitors.registeredNodeToEpoch()
		)
		.addActor(NodeValidatorRegistrator.class);

	@Test
	public void when_increasing_validators__then_they_should_be_getting_registered() {
		SimulationTest simulationTest = bftTestBuilder
			.build();

		final var runningTest = simulationTest.run();
		final var checkResults = runningTest.awaitCompletion();

		assertThat(checkResults).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}
}
