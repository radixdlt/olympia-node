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

package com.radixdlt.integration.distributed.deterministic.tests.consensus_ledger_epochs;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import java.util.LinkedList;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.environment.deterministic.network.ChannelId;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.MessageMutator;

import static com.radixdlt.environment.deterministic.network.MessageSelector.*;

public class MovingWindowValidatorsTest {

	private static LongFunction<IntStream> windowedEpochToNodesMapper(int windowSize, int totalValidatorCount) {
		// Epoch starts at 1, and we want base 0, so subtract 1
		return epoch -> IntStream.range(0, windowSize).map(index -> (int) (epoch - 1 + index) % totalValidatorCount);
	}

	private void run(int numNodes, int windowSize, long maxEpoch, View highView) {
		DeterministicTest bftTest = DeterministicTest.builder()
			.numNodes(numNodes)
			.epochHighView(highView)
			.messageMutator(mutator())
			.messageSelector(firstSelector())
			.epochNodeIndexesMapping(windowedEpochToNodesMapper(windowSize, numNodes))
			.build()
			.runUntil(DeterministicTest.hasReachedEpochView(EpochView.of(maxEpoch, highView)));

		LinkedList<SystemCounters> testCounters = systemCounters(bftTest);
		assertThat(testCounters).extracting(sc -> sc.get(CounterType.BFT_INDIRECT_PARENT)).containsOnly(0L);
		assertThat(testCounters).extracting(sc -> sc.get(CounterType.BFT_TIMEOUT)).containsOnly(0L);

		long maxCount = maxProcessedFor(numNodes, windowSize, maxEpoch, highView.number());

		assertThat(testCounters)
			.extracting(sc -> sc.get(CounterType.BFT_PROCESSED))
			.allMatch(between(maxCount - maxEpoch, maxCount));
	}

	private MessageMutator mutator() {
		return (message, queue) -> {
			if (Epoched.isInstance(message.message(), ScheduledLocalTimeout.class)) {
				// Discard
				return true;
			}
			// Process others in arrival order, local first.
			// Need to make sure EpochsLedgerUpdate is processed before consensus messages for the new epoch
			if (nonLocalMessage(message)) {
				queue.add(message.withArrivalTime(0));
			} else {
				queue.addBefore(message.withArrivalTime(0), this::nonLocalMessage);
			}
			return true;
		};
	}

	private boolean nonLocalMessage(ControlledMessage msg) {
		ChannelId channelId = msg.channelId();
		return channelId.senderIndex() != channelId.receiverIndex();
	}

	@Test
	public void given_correct_1_node_bft_with_4_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_postconditions() {
		run(4, 1, 100L, View.of(100));
	}

	@Test
	public void given_correct_3_node_bft_with_4_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_postconditions() {
		run(4, 3, 120L, View.of(100));
	}

	@Test
	public void given_correct_25_node_bft_with_50_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_postconditions() {
		run(50, 25, 100L, View.of(100));
	}

	@Test
	public void given_correct_25_node_bft_with_100_total_nodes_with_changing_epochs_per_1_view__then_should_pass_bft_and_postconditions() {
		run(100, 25, 100L, View.of(100));
	}

	private static long maxProcessedFor(int numNodes, int numValidators, long epochs, long epochHighView) {
		return epochHighView * epochs * numValidators / numNodes;
	}

	private static LinkedList<SystemCounters> systemCounters(DeterministicTest bftTest) {
		return IntStream.range(0, bftTest.numNodes())
			.mapToObj(bftTest::getSystemCounters)
			.collect(Collectors.toCollection(LinkedList::new));
	}

	private static Predicate<Long> between(long lower, long upper) {
		return value -> value >= lower && value <= upper;
	}
}
