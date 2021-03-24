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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger;

import com.google.common.collect.ImmutableList;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkDroppers;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

import java.time.Duration;
import java.util.stream.LongStream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * When running a network with 3 nodes, where all proposals are dropped,
 * leader should be able to re-send his vote with a timeout flag and the vote
 * should be accepted by all other nodes, resulting in moving to a next view (once remaining two nodes also timeout).
 * If the timeout replacement votes are not accepted, then we loose a round because a node can only
 * move to the next view when it hasn't received the original non-timeout vote.
 * This test checks that all nodes only need a single timeout event to proceed to next view, even the node that
 * initially received a non-timeout vote (next leader), meaning that it must have successfully replaced
 * a non-timeout vote with a timeout vote in the same view.
 */
public class TimeoutPreviousVoteWithDroppedProposalsTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(3)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed(),
			NetworkDroppers.dropAllProposals()
		)
		.ledger()
		.addTestModules(
			ConsensusMonitors.safety(),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered()
		);

	@Test
	public void sanity_test() {
		SimulationTest test = bftTestBuilder.build();
		final var runningTest = test.run(Duration.ofSeconds(10));
		final var results = runningTest.awaitCompletion();

		final var statistics = runningTest.getNetwork().getSystemCounters().values().stream()
			.map(s -> LongStream.of(s.get(CounterType.BFT_TIMEOUT), s.get(CounterType.BFT_TIMED_OUT_VIEWS)))
			.map(LongStream::summaryStatistics)
			.collect(ImmutableList.toImmutableList());

		statistics.forEach(s -> {
			// to make sure we've processed some views
			assertTrue(s.getMin() > 2);

			// this ensures that we only need a single timeout per view
			// BFT_TIMEOUT equal to BFT_TIMED_OUT_VIEWS
			assertEquals(s.getMin(), s.getMax());
		});

		assertThat(results).allSatisfy((name, error) ->
			AssertionsForClassTypes.assertThat(error).isNotPresent()
		);
	}
}
