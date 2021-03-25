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

package com.radixdlt.integration.distributed.deterministic.tests.consensus_ledger_epochs;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import io.reactivex.rxjava3.schedulers.Timed;
import org.junit.Test;

import java.util.Random;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessCachedEventsWithTimeoutCertTest {

	private static final int TEST_NODE = 4;
	private final Random random = new Random(123456);

	@Test
	public void process_cached_sync_event_with_tc_test() {
		final var test = DeterministicTest.builder()
			.numNodes(5)
			.messageSelector(MessageSelector.randomSelector(random))
			.messageMutators(
				dropProposalToNodes(View.of(1), ImmutableList.of(TEST_NODE)),
				dropProposalToNodes(View.of(2), ImmutableList.of(2, 3, TEST_NODE)),
				dropVotesForNode(TEST_NODE)
			)
			.buildWithEpochs(View.of(100))
			.runUntil(nodeVotesForView(View.of(3), TEST_NODE));

		// just to check if the node indeed needed to sync
		final var counters = test.getSystemCounters(TEST_NODE);
		assertThat(counters.get(SystemCounters.CounterType.BFT_TIMEOUT_QUORUMS)).isEqualTo(0);
		assertThat(counters.get(SystemCounters.CounterType.BFT_VOTE_QUORUMS)).isEqualTo(0);
	}

	private static MessageMutator dropProposalToNodes(View view, ImmutableList<Integer> nodes) {
		return (message, queue) -> {
			final var msg = message.message();
			if (msg instanceof Proposal) {
				final Proposal proposal = (Proposal) msg;
				return proposal.getView().equals(view)
					&& nodes.contains(message.channelId().receiverIndex());
			}
			return false;
		};
	}

	private static MessageMutator dropVotesForNode(int node) {
		return (message, queue) -> {
			final var msg = message.message();
			if (msg instanceof Vote) {
				return message.channelId().receiverIndex() == node;
			}
			return false;
		};
	}

	public static Predicate<Timed<ControlledMessage>> nodeVotesForView(View view, int node) {
		return timedMsg -> {
			final var message = timedMsg.value();
			if (!(message.message() instanceof Vote)) {
				return false;
			}
			final var vote = (Vote) message.message();
			return vote.getView().equals(view)
				&& message.channelId().senderIndex() == node;
		};
	}

}
