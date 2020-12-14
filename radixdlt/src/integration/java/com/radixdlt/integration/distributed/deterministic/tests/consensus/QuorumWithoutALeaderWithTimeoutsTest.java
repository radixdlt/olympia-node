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

import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import org.junit.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When original votes (to next view leader, non timed out) are dropped,
 * nodes should be able to resend those votes to each other (with timeout)
 * and form the quorum themselves.
 * As a result, there should be no timeout (non-QC) quorums and no indirect parents.
 */
public class QuorumWithoutALeaderWithTimeoutsTest {

	private final Random random = new Random(123456);

	private void run(int numNodes, long numViews) {
		final DeterministicTest test = DeterministicTest.builder()
			.numNodes(numNodes)
			.messageSelector(MessageSelector.randomSelector(random))
			.messageMutator(dropAllNonTimeoutVotes())
			.build()
			.runUntil(DeterministicTest.hasReachedView(View.of(numViews)));

		for (int nodeIndex = 0; nodeIndex < numNodes; ++nodeIndex) {
			final SystemCounters counters = test.getSystemCounters(nodeIndex);
			final long numberOfIndirectParents = counters.get(CounterType.BFT_INDIRECT_PARENT);
			final long totalNumberOfTimeouts = counters.get(CounterType.BFT_TIMEOUT);
			final long totalNumberOfTimeoutQuorums = counters.get(CounterType.BFT_TIMEOUT_QUORUMS);
			final long totalNumberOfVoteQuorums = counters.get(CounterType.BFT_VOTE_QUORUMS);
			assertThat(totalNumberOfTimeoutQuorums).isEqualTo(0); // no TCs
			assertThat(numberOfIndirectParents).isEqualTo(0); // no indirect parents
			assertThat(totalNumberOfTimeouts).isEqualTo(numViews - 1); // a timeout for each view
			assertThat(totalNumberOfVoteQuorums).isBetween(numViews - 2, numViews); // quorum count matches views
		}
	}

	private static MessageMutator dropAllNonTimeoutVotes() {
		return (message, queue) -> {
			final Object msg = message.message();
			if (msg instanceof Vote) {
				final Vote vote = (Vote) msg;
				return vote.getTimeoutSignature().isEmpty();
			}
			return false;
		};
	}

	@Test
	public void when_run_3_correct_nodes_for_50k_views__then_bft_should_be_responsive() {
		this.run(3, 50_000);
	}

	@Test
	public void when_run_10_correct_nodes_with_for_2k_views__then_bft_should_be_responsive() {
		this.run(10, 2000);
	}

	@Test
	public void when_run_100_correct_nodes_with_for_300_views__then_bft_should_be_responsive() {
		this.run(100, 300);
	}
}
