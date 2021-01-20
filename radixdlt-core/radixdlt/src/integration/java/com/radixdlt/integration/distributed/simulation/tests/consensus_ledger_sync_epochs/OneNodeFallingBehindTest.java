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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_sync_epochs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkDroppers;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import java.time.Duration;
import java.util.LongSummaryStatistics;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Get the system into a configuration where one node needs to catch up to
 * BFT but is slowed down by Ledger sync.
 */
public class OneNodeFallingBehindTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(10)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed(),
			NetworkDroppers.dropAllMessagesForOneNode(10000, 10000)
		)
		.pacemakerTimeout(1000)
		.ledgerAndEpochsAndSync(View.of(100), epoch -> IntStream.range(0, 10), 200)
		.checkLedgerInOrder()
		.checkLedgerProcessesConsensusCommitted()
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(30, TimeUnit.SECONDS),
			ConsensusMonitors.vertexRequestRate(50) // Conservative check
		);

	@Test
	public void sanity_test() {
		SimulationTest test = bftTestBuilder.build();
		TestResults results = test.run(Duration.ofSeconds(60));

		LongSummaryStatistics statistics = results.getNetwork().getSystemCounters().values().stream()
			.map(s -> s.get(CounterType.BFT_SYNC_REQUESTS_SENT))
			.mapToLong(l -> l)
			.summaryStatistics();

		System.out.println(statistics);

		assertThat(results.getCheckResults()).allSatisfy((name, error) -> AssertionsForClassTypes.assertThat(error).isNotPresent());
	}

}
