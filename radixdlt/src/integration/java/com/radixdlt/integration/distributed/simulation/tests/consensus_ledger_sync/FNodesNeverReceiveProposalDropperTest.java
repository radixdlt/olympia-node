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

import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.NetworkDroppers;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import java.util.Arrays;
import java.util.Collection;
import java.util.LongSummaryStatistics;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FNodesNeverReceiveProposalDropperTest {
	@Parameters
	public static Collection<Object[]> numNodes() {
		return Arrays.asList(new Object[][] {
			{4}, {100}
		});
	}

	private final Builder bftTestBuilder;

	public FNodesNeverReceiveProposalDropperTest(int numNodes) {
		this.bftTestBuilder = SimulationTest.builder()
			.numNodes(numNodes)
			.networkModules(
				NetworkOrdering.inOrder(),
				NetworkLatencies.fixed(10),
				NetworkDroppers.fNodesAllReceivedProposalsDropped()
			)
			.pacemakerTimeout(5000)
			.ledgerAndSync()
			.checkConsensusSafety("safety")
			.checkConsensusLiveness("liveness", 5000, TimeUnit.MILLISECONDS)
			.checkConsensusAllProposalsHaveDirectParents("directParents")
			.checkLedgerInOrder("ledgerInOrder")
			.checkLedgerProcessesConsensusCommitted("consensusToLedger");
	}

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
