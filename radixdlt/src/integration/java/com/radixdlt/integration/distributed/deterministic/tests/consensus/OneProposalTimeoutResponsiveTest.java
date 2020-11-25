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

import com.radixdlt.counters.SystemCounters.CounterType;
import java.util.Random;
import org.junit.Test;

import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.counters.SystemCounters;

import static org.assertj.core.api.Assertions.assertThat;

public class OneProposalTimeoutResponsiveTest {
	private final Random random = new Random(123456);

	private void run(int numNodes, long numViews, long dropPeriod) {
		DeterministicTest test = DeterministicTest.builder()
			.numNodes(numNodes)
			.messageSelector(MessageSelector.randomSelector(random))
			.messageMutator(dropSomeProposals(dropPeriod))
			.build()
			.runUntil(DeterministicTest.hasReachedView(View.of(numViews)));

		long requiredIndirectParents = (numViews - 1) / dropPeriod; // Edge case if dropPeriod a factor of numViews

		long requiredTimeouts = numViews / dropPeriod * 2;
		for (int nodeIndex = 0; nodeIndex < numNodes; ++nodeIndex) {
			SystemCounters counters = test.getSystemCounters(nodeIndex);
			long numberOfIndirectParents = counters.get(SystemCounters.CounterType.BFT_INDIRECT_PARENT);
			long totalNumberOfTimeouts = counters.get(CounterType.BFT_TIMEOUT);
			assertThat(numberOfIndirectParents).isEqualTo(requiredIndirectParents);
			assertThat(totalNumberOfTimeouts).isEqualTo(requiredTimeouts);
		}
	}

	private static MessageMutator dropSomeProposals(long dropPeriod) {
		return (message, queue) -> {
			Object msg = message.message();
			if (msg instanceof Proposal) {
				final Proposal proposal = (Proposal) msg;
				final View view = proposal.getVertex().getView();
				final long viewNumber = view.number();

				return viewNumber % dropPeriod == 0;
			}
			return false;
		};
	}

	@Test
	public void when_run_3_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(3, 50_000, 100);
	}

	@Test
	public void when_run_4_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(4, 50_000, 100);
	}

	@Test
	public void when_run_100_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(100, 1_000, 100);
	}
}
