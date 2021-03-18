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

package com.radixdlt.integration.distributed.deterministic.tests.bft_sync;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
* If quorum is formed on a timeout (timeout certificate), and there's a node that's a single view behind
 * (i.e. it didn't participate in forming of TC). Then it should be able to sync up (move to next view)
 * as soon as it receives a proposal (with a TC). BFTSync should then immediately switch to next view
 * without any additional sync requests.
 * The setup is as follows:
 * 1. there are 4 nodes
 * 2. proposal is sent by the leader (0) but only received by 2 nodes (including the leader): 0 and 1
 * 3. two nodes vote on a proposal (0 and 1)
 * 4. two nodes vote on an empty timeout vertex and broadcast the vote (2 and 3)
 * 5. nodes 0 and 1 resend (broadcast) their vote with a timeout flag
 * 6. node 0 doesn't receive any of the above votes
 * 7. nodes 1, 2 and 3 can form a valid TC out of the votes they received, and they switch to the next view
 * 8. next leader (node 1) sends out a proposal
 * 9. proposal (with a valid TC) is received by node 0 (which is still on previous view)
 * 10. node 0 is able to move to the next view just by processing the proposal's TC (no additional sync requests)
 * Expected result: node 0 is at view 2 and no sync requests have been sent
 */
public class SyncToTimeoutQcTest {

	private static final int NUM_NODES = 4;

	private final Random random = new Random(123456);

	@Test
	public void sync_to_timeout_qc_test() {
		final DeterministicTest test = DeterministicTest.builder()
			.numNodes(NUM_NODES)
			.messageSelector(MessageSelector.randomSelector(random))
			.messageMutator(
				dropProposalsToNodes(ImmutableSet.of(2, 3))
					.andThen(dropVotesToNode(0))
			)
			.buildWithLedgerAndEpochs(View.of(10))
			.runUntil(DeterministicTest.viewUpdateOnNode(View.of(2), 0));

		for (int nodeIndex = 0; nodeIndex < NUM_NODES; ++nodeIndex) {
			final var counters = test.getSystemCounters(nodeIndex);
			// no bft sync requests were needed
			assertEquals(0, counters.get(CounterType.BFT_SYNC_REQUESTS_SENT));
		}
	}

	private static MessageMutator dropVotesToNode(int nodeIndex) {
		return (message, queue) -> {
			final var msg = message.message();
			if (msg instanceof Vote) {
				return message.channelId().receiverIndex() == nodeIndex;
			}
			return false;
		};
	}

	private static MessageMutator dropProposalsToNodes(ImmutableSet<Integer> nodesIndices) {
		return (message, queue) -> {
			final var msg = message.message();
			if (msg instanceof Proposal) {
				return nodesIndices.contains(message.channelId().receiverIndex());
			}
			return false;
		};
	}
}
