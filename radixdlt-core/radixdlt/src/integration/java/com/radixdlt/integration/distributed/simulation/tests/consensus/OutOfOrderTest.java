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

package com.radixdlt.integration.distributed.simulation.tests.consensus;

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkDroppers;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import java.util.Collection;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test where network does not guarantee ordering of messages.
 * BFT logic including BFT-sync should not be dependent on
 * message ordering so all properties of the system should hold intact.
 */
@RunWith(Parameterized.class)
public final class OutOfOrderTest {
	private static final Logger logger = LogManager.getLogger();

	@Parameters
	public static Collection<Object[]> numNodes() {
		return List.of(new Object[][] {
			{4}, {10}
		});
	}

	private final int minLatency = 10;
	private final int maxLatency = 200;
	private final Builder bftTestBuilder;

	public OutOfOrderTest(int numNodes) {
		this.bftTestBuilder = SimulationTest.builder()
			.numNodes(numNodes)
			.networkModules(
				NetworkOrdering.outOfOrder(),
				NetworkLatencies.random(minLatency, maxLatency),
				NetworkDroppers.fRandomProposalsPerViewDropped()
			)
			.pacemakerTimeout(5000)
			.addTestModules(
				ConsensusMonitors.safety(),
				ConsensusMonitors.liveness(5000, TimeUnit.MILLISECONDS),
				ConsensusMonitors.directParents()
			);
	}

	@Test
	public void out_of_order_messaging_should_not_affect_properties_of_system() {
		SimulationTest test = bftTestBuilder
			.build();

		TestResults results = test.run();
		LongSummaryStatistics statistics = results.getNetwork().getSystemCounters().values().stream()
			.map(s -> s.get(CounterType.BFT_SYNC_REQUESTS_SENT))
			.mapToLong(l -> l)
			.summaryStatistics();
		logger.info(statistics);
		assertThat(results.getCheckResults()).allSatisfy((name, error) -> assertThat(error).isNotPresent());
	}
}
