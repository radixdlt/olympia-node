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

import com.google.common.collect.Lists;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import com.radixdlt.integration.distributed.deterministic.configuration.SyncedExecutorFactories;
import com.radixdlt.integration.distributed.deterministic.network.ChannelId;
import com.radixdlt.integration.distributed.deterministic.network.ControlledMessage;
import com.radixdlt.integration.distributed.deterministic.network.MessageMutator;
import com.radixdlt.integration.distributed.deterministic.network.MessageRank;
import com.radixdlt.integration.distributed.deterministic.network.MessageSelector;
import com.radixdlt.utils.Pair;

import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.LinkedList;
import org.junit.Test;

public class OneSlowNodeTest {

	/**
	 * TODO: Convert this into a steady state test
	 */
	@Test
	public void when_three_fast_nodes_and_one_slow_node_two_cycles__then_missing_parent_should_not_cause_sync_exception() {
		final int numNodes = 4;

		LinkedList<Pair<ChannelId, Class<?>>> expectedSequence = Lists.newLinkedList();
		for (int curLeader = 1; curLeader <= 2; curLeader++) {
			expectedSequence.add(Pair.of(ChannelId.of(1, curLeader), NewView.class));
			expectedSequence.add(Pair.of(ChannelId.of(2, curLeader), NewView.class));
			expectedSequence.add(Pair.of(ChannelId.of(3, curLeader), NewView.class));

			expectedSequence.add(Pair.of(ChannelId.of(curLeader, 1), Proposal.class));
			expectedSequence.add(Pair.of(ChannelId.of(curLeader, 2), Proposal.class));
			expectedSequence.add(Pair.of(ChannelId.of(curLeader, 3), Proposal.class));

			expectedSequence.add(Pair.of(ChannelId.of(1, curLeader), Vote.class));
			expectedSequence.add(Pair.of(ChannelId.of(2, curLeader), Vote.class));
			expectedSequence.add(Pair.of(ChannelId.of(3, curLeader), Vote.class));
		}
		// Delayed initial NewView from node 0 to (then) leader 1
		expectedSequence.add(Pair.of(ChannelId.of(0, 1), NewView.class));
		// Delayed initial Proposal from (then) leader 1 to node 0
		expectedSequence.add(Pair.of(ChannelId.of(1, 0), Proposal.class));

		DeterministicTest.builder()
			.numNodes(numNodes)
			.syncedExecutorFactory(SyncedExecutorFactories.alwaysSynced())
			.messageSelector(sequenceSelector(expectedSequence))
			.messageMutator(stopWhenEmpty(expectedSequence).otherwise(delayMessagesForNode(0)))
			.build()
			.run();
	}

	private MessageSelector sequenceSelector(LinkedList<Pair<ChannelId, Class<?>>> expectedSequence) {
		return messages -> {
			final Pair<ChannelId, Class<?>> messageDetails = expectedSequence.pop();
			final ChannelId expectedChannel = messageDetails.getFirst();
			final Class<?> expectedMsgClass = messageDetails.getSecond();
			for (ControlledMessage message : messages) {
				if (expectedChannel.equals(message.channelId())) {
					Class<?> msgClass = message.message().getClass();
					if (expectedMsgClass.isAssignableFrom(msgClass)) {
						return message;
					}
				}
			}
			fail(String.format("Can't find %s message %s: %s", expectedMsgClass.getSimpleName(), expectedChannel, messages));
			return null; // Not required, but compiler can't tell that fail throws exception
		};
	}

	private MessageMutator stopWhenEmpty(Collection<?> expectedSequence) {
		return (rank, message, queue) -> !expectedSequence.isEmpty();
	}

	private MessageMutator delayMessagesForNode(int index) {
		return (rank, message, queue) -> {
			final int receiverIndex = message.channelId().receiverIndex();
			final int senderIndex = message.channelId().senderIndex();
			final MessageRank rankToUse = receiverIndex == index || senderIndex == index
				? MessageRank.of(rank.major(), rank.minor() + 1)
				: rank;
			queue.add(rankToUse, message);
			return true;
		};
	}
}
