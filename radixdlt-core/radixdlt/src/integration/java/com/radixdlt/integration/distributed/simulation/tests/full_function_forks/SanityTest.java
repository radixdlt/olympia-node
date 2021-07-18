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

package com.radixdlt.integration.distributed.simulation.tests.full_function_forks;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.statecomputer.forks.ForkOverwritesWithShorterEpochsModule;
import com.radixdlt.integration.distributed.simulation.monitors.application.ApplicationMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.application.RadixEngineUniqueGenerator;
import com.radixdlt.integration.distributed.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@RunWith(Parameterized.class)
public class SanityTest {
	@Parameterized.Parameters
	public static Collection<Object[]> fees() {
		return List.of(new Object[][] {
			{UInt256.ZERO}, {UInt256.ONE},
		});
	}

	private static final Logger logger = LogManager.getLogger();
	private final Builder bftTestBuilder;

	public SanityTest(UInt256 perByteFee) {
		logger.info("Test fees={}", perByteFee);
		bftTestBuilder = SimulationTest.builder()
			.numNodes(4)
			.pacemakerTimeout(3000)
			.networkModules(
				NetworkOrdering.inOrder(),
				NetworkLatencies.fixed()
			)
			.fullFunctionNodes(SyncConfig.of(400L, 10, 2000L))
			.addRadixEngineConfigModules(
				new MainnetForkConfigsModule(),
				new ForkOverwritesWithShorterEpochsModule(
					new RERulesConfig(
						Set.of("xrd"),
						FeeTable.noFees(),
						OptionalInt.of(5),
						10,
						2,
						Amount.ofTokens(10),
						1,
						Amount.ofTokens(10),
						9800,
						10
					)),
				new ForksModule()
			)
			.addNodeModule(MempoolConfig.asModule(1000, 10))
			.addTestModules(
				ConsensusMonitors.safety(),
				ConsensusMonitors.liveness(3, TimeUnit.SECONDS),
				ConsensusMonitors.noTimeouts(),
				ConsensusMonitors.directParents(),
				LedgerMonitors.consensusToLedger(),
				LedgerMonitors.ordered(),
				RadixEngineMonitors.noInvalidProposedCommands()
			)
			.addMempoolSubmissionsSteadyState(RadixEngineUniqueGenerator.class);

		if (perByteFee.isZero()) {
			bftTestBuilder.addTestModules(ApplicationMonitors.mempoolCommitted());
		}
	}

	@Test
	public void sanity_tests_should_pass() {
		SimulationTest simulationTest = bftTestBuilder
			.build();

		final var results = simulationTest
			.run(Duration.ofMinutes(1)).awaitCompletion();
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}
}
