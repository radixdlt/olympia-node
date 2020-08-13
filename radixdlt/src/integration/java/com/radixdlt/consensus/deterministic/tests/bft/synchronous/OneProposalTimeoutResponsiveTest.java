/*
 *
 *  * (C) Copyright 2020 Radix DLT Ltd
 *  *
 *  * Radix DLT Ltd licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except in
 *  * compliance with the License.  You may obtain a copy of the
 *  * License at
 *  *
 *  *  http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  * either express or implied.  See the License for the specific
 *  * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.consensus.deterministic.tests.bft.synchronous;

import java.util.Random;
import java.util.Set;

import org.assertj.core.util.Sets;
import org.junit.Test;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.deterministic.DeterministicTest;
import com.radixdlt.counters.SystemCounters;

import static org.assertj.core.api.Assertions.assertThat;

public class OneProposalTimeoutResponsiveTest {
	private final Random random = new Random(123456);
	private final Set<Integer> nodesCompleted = Sets.newHashSet();

	private void run(int numNodes, long numViews, long dropPeriod) {
		final DeterministicTest test = DeterministicTest.createSingleEpochAlwaysSyncedWithTimeoutsTest(numNodes);
		test.start();

		nodesCompleted.clear();
		while (nodesCompleted.size() < numNodes) {
			test.processNextMsgWithSenderAndReceiver(
				random,
				(senderId, receiverId, msg) -> processMessage(senderId, msg, numViews, dropPeriod));
		}

		long requiredIndirectParents = (numViews - 1) / dropPeriod; // Edge case if dropPeriod a factor of numViews
		long requiredTimeouts = numViews / dropPeriod;

		for (int nodeIndex = 0; nodeIndex < numNodes; ++nodeIndex) {
			SystemCounters counters = test.getSystemCounters(nodeIndex);
			long numberOfIndirectParents = counters.get(SystemCounters.CounterType.BFT_INDIRECT_PARENT);
			long numberOfTimeouts = counters.get(SystemCounters.CounterType.BFT_TIMEOUT);
			assertThat(numberOfIndirectParents).isEqualTo(requiredIndirectParents);
			// Not every node will timeout on a dropped proposal, as 2f+1 nodes will timeout and then continue.
			// The remaining f nodes will sync rather than timing out.
			// The lower bound of the following test is likely to pass, but not guaranteed by anything here.
			assertThat(numberOfTimeouts).isBetween(1L, requiredTimeouts);
		}
	}

	private boolean processMessage(int senderId, Object msg, long numViews, long dropPeriod) {
		if (msg instanceof NewView) {
			NewView nv = (NewView) msg;
			if (nv.getView().number() > numViews) {
				this.nodesCompleted.add(senderId);
				return false;
			}
		}
		if (msg instanceof Proposal) {
			final Proposal proposal = (Proposal) msg;
			final View view = proposal.getVertex().getView();
			final long viewNumber = view.number();

			return viewNumber % dropPeriod != 0;
		}
		return true;
	}

	@Test
	public void when_run_3_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(3, 300_000, 100);
	}

	@Test
	public void when_run_4_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		this.run(4, 300_000, 100);
	}

	@Test
	public void when_run_100_correct_nodes_with_1_timeout__then_bft_should_be_responsive() {
		// FIXME: Could increase frequency once sync issues resolved.
		this.run(100, 30_000, 1000);
	}
}
