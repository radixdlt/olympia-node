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

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.RandomLatencyModule;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.integration.distributed.simulation.network.OneProposalPerViewDropper;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.MessageInTransit;
import java.util.LongSummaryStatistics;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

public class OneNodeNeverReceiveProposalDropperTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.networkModule(Modules.combine(
			new RandomLatencyModule(10, 200),
			new AbstractModule() {
				@ProvidesIntoSet
				Predicate<MessageInTransit> dropper(ImmutableList<BFTNode> nodes) {
					return new OneProposalPerViewDropper(nodes.subList(0, 1), new Random());
				}
			}
		))
		.pacemakerTimeout(5000)
		.ledgerAndSync()
		.checkConsensusSafety("safety")
		.checkConsensusLiveness("liveness", 5000, TimeUnit.MILLISECONDS)
		.checkConsensusNoTimeouts("noTimeouts")
		.checkConsensusAllProposalsHaveDirectParents("directParents")
		.checkLedgerInOrder("ledgerInOrder")
		.checkLedgerProcessesConsensusCommitted("consensusToLedger");

	@Test
	public void sanity_tests_should_pass() {
		SimulationTest simulationTest = bftTestBuilder
			.build();
		TestResults results = simulationTest.run();
		assertThat(results.getCheckResults()).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());

		LongSummaryStatistics statistics = results.getNetwork().getSystemCounters().values().stream()
			.map(s -> s.get(CounterType.SYNC_PROCESSED))
			.mapToLong(l -> l)
			.summaryStatistics();

		System.out.println(statistics);
		assertThat(statistics.getSum()).isGreaterThan(0L);
	}

}
