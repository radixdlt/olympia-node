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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.IncorrectAlwaysAcceptingAccumulatorVerifierModule;
import com.radixdlt.integration.distributed.SometimesByzantineCommittedReader;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.sync.CommittedReader;
import java.util.LongSummaryStatistics;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Any number/sort of byzantine sync modules should never be able to cause
 * a safety failure.
 */
public class ByzantineSyncTest {
	Logger logger = LogManager.getLogger();
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.randomLatency(10, 200)
		.addByzantineModuleToAll(new AbstractModule() {
			@Override
			protected void configure() {
				Multibinder<LedgerUpdateSender> committedSenders = Multibinder.newSetBinder(binder(), LedgerUpdateSender.class);
				committedSenders.addBinding().to(SometimesByzantineCommittedReader.class).in(Scopes.SINGLETON);
				bind(CommittedReader.class).to(SometimesByzantineCommittedReader.class).in(Scopes.SINGLETON);
			}
		})
		.pacemakerTimeout(5000)
		.addOneNodeNeverReceiveProposalDropper()
		.ledgerAndSync()
		.checkConsensusSafety("safety")
		.checkConsensusLiveness("liveness", 5000, TimeUnit.MILLISECONDS)
		.checkConsensusNoTimeouts("noTimeouts")
		.checkConsensusAllProposalsHaveDirectParents("directParents")
		.checkLedgerInOrder("ledgerInOrder")
		.checkLedgerProcessesConsensusCommitted("consensusToLedger");

	@Test
	public void given_a_sometimes_byzantine_sync_layer__sanity_tests_should_pass() {
		SimulationTest simulationTest = bftTestBuilder
			.build();
		TestResults results = simulationTest.run();
		assertThat(results.getCheckResults()).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());

		LongSummaryStatistics statistics = results.getNetwork().getSystemCounters().values().stream()
			.map(s -> s.get(CounterType.SYNC_PROCESSED))
			.mapToLong(l -> l)
			.summaryStatistics();

		System.out.println(statistics);
		AssertionsForClassTypes.assertThat(statistics.getSum()).isGreaterThan(0L);
	}

	@Test
	public void given_a_sometimes_byzantine_sync_layer_with_incorrect_accumulator_verifier__sanity_tests_should_not_pass() {
		SimulationTest simulationTest = bftTestBuilder
			.overrideWithIncorrectModule(new IncorrectAlwaysAcceptingAccumulatorVerifierModule())
			.build();
		TestResults results = simulationTest.run();
		assertThat(results.getCheckResults()).hasEntrySatisfying("ledgerInOrder", error -> assertThat(error).isPresent());
		LongSummaryStatistics statistics = results.getNetwork().getSystemCounters().values().stream()
			.map(s -> s.get(CounterType.SYNC_PROCESSED))
			.mapToLong(l -> l)
			.summaryStatistics();

		logger.info(statistics);
	}
}
