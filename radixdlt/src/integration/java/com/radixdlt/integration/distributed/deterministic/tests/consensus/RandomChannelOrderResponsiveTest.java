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

package com.radixdlt.integration.distributed.deterministic.tests.consensus;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import com.radixdlt.integration.distributed.deterministic.configuration.SyncedExecutorFactories;
import com.radixdlt.integration.distributed.deterministic.network.MessageMutator;
import com.radixdlt.integration.distributed.deterministic.network.MessageSelector;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

public class RandomChannelOrderResponsiveTest {
	@Test
	public void when_run_4_correct_nodes_with_channel_order_random_and_timeouts_disabled__then_bft_should_be_responsive() {
		final int numNodes = 4;
		final int viewsToRun = 2500 * numNodes; // numNodes should be a factor of this number
		final Random random = new Random(12345);

		DeterministicTest test = DeterministicTest.builder()
			.numNodes(numNodes)
			.syncedExecutorFactory(SyncedExecutorFactories.alwaysSynced())
			.messageSelector(MessageSelector.selectAndStopAt(MessageSelector.randomSelector(random), View.of(viewsToRun)))
			.messageMutator(MessageMutator.dropTimeouts())
			.build()
			.run();

		List<Long> proposalsMade = IntStream.range(0, numNodes)
			.mapToObj(test::getSystemCounters)
			.map(counters -> counters.get(CounterType.BFT_PROPOSALS_MADE))
			.collect(ImmutableList.toImmutableList());

		assertThat(proposalsMade).allMatch(l -> l == viewsToRun / numNodes);
	}

	@Test
	public void when_run_100_correct_nodes_with_channel_order_random_and_timeouts_disabled__then_bft_should_be_responsive() {
		final int numNodes = 100;
		final int viewsToRun = 50 * numNodes; // numNodes should be a factor of this number
		final Random random = new Random(12345);

		DeterministicTest test = DeterministicTest.builder()
			.numNodes(numNodes)
			.syncedExecutorFactory(SyncedExecutorFactories.alwaysSynced())
			.messageSelector(MessageSelector.selectAndStopAt(MessageSelector.randomSelector(random), View.of(viewsToRun)))
			.build()
			.run();

		List<Long> proposalsMade = IntStream.range(0, numNodes)
			.mapToObj(test::getSystemCounters)
			.map(counters -> counters.get(CounterType.BFT_PROPOSALS_MADE))
			.collect(ImmutableList.toImmutableList());

		assertThat(proposalsMade).allMatch(l -> l == viewsToRun / numNodes);
	}
}
