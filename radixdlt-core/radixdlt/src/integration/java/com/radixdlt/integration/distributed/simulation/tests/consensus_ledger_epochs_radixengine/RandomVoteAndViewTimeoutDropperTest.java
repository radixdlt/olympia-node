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

import com.radixdlt.MainnetForkConfigsModule;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkDroppers;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.radixdlt.integration.distributed.simulation.application.NodeValidatorRandomRegistrator;
import com.radixdlt.integration.distributed.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import org.apache.commons.collections4.MapUtils;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

public class RandomVoteAndViewTimeoutDropperTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(8, 4)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed(),
			NetworkDroppers.randomVotesAndViewTimeoutsDropped(0.2)
		)
		.addRadixEngineConfigModules(
			new MainnetForkConfigsModule(),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault().overrideMaxSigsPerRound(5)),
			new ForksModule()
		)
		.ledgerAndRadixEngineWithEpochHighView()
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(20, TimeUnit.SECONDS),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered(),
			RadixEngineMonitors.noInvalidProposedCommands()
		)
		.addActor(NodeValidatorRandomRegistrator.class);

	@Test
	public void when_random_validators__then_sanity_checks_should_pass() {
		SimulationTest simulationTest = bftTestBuilder.build();
		final var runningTest = simulationTest.run(Duration.of(2, ChronoUnit.MINUTES));
		final var checkResults = runningTest.awaitCompletion();

		List<CounterType> counterTypes = List.of(
			CounterType.BFT_VERTEX_STORE_FORKS,
			CounterType.BFT_PROCESSED,
			CounterType.BFT_TIMEOUT,
			CounterType.LEDGER_STATE_VERSION
		);

		Map<CounterType, LongSummaryStatistics> statistics = counterTypes.stream()
			.collect(Collectors.toMap(
				counterType -> counterType,
				counterType -> runningTest.getNetwork().getSystemCounters().values()
					.stream()
					.mapToLong(s -> s.get(counterType)).summaryStatistics())
			);

		MapUtils.debugPrint(System.out, "statistics", statistics);

		assertThat(checkResults).allSatisfy((name, error) -> AssertionsForClassTypes.assertThat(error).isNotPresent());
	}
}
