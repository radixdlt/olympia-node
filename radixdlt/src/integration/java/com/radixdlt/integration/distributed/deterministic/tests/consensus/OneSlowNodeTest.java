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
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import com.radixdlt.integration.distributed.deterministic.network.ChannelId;
import com.radixdlt.integration.distributed.deterministic.network.ControlledMessage;
import com.radixdlt.integration.distributed.deterministic.network.MessageMutator;
import com.radixdlt.integration.distributed.deterministic.network.MessageSelector;
import com.radixdlt.utils.Pair;

import static org.junit.Assert.fail;

import java.util.LinkedList;
import org.junit.Test;

public class OneSlowNodeTest {

	/**
	 * TODO: Convert this into a steady state test
	 */
	@Test
	public void when_three_fast_nodes_and_one_slow_node_two_cycles__then_missing_parent_should_not_cause_sync_exception() {
		final int numNodes = 4;

		LinkedList<Pair<ChannelId, Class<?>>> processingSequence = Lists.newLinkedList();
		// Local proposal first
		processingSequence.add(Pair.of(ChannelId.of(1, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 3), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), BFTUpdate.class));

		processingSequence.add(Pair.of(ChannelId.of(1, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), Vote.class));

		// Local proposal first
		processingSequence.add(Pair.of(ChannelId.of(2, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 3), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), BFTUpdate.class));

		processingSequence.add(Pair.of(ChannelId.of(3, 3), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 3), Vote.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 3), Vote.class));

		// Local proposal from 3->3
		processingSequence.add(Pair.of(ChannelId.of(3, 3), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 3), BFTUpdate.class));

		// Delayed initial Proposal from (then) leader 1 to node 0
		processingSequence.add(Pair.of(ChannelId.of(1, 0), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(0, 0), BFTUpdate.class));

		// Other proposals from 3
		processingSequence.add(Pair.of(ChannelId.of(3, 1), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(1, 1), BFTUpdate.class));
		processingSequence.add(Pair.of(ChannelId.of(3, 2), Proposal.class));
		processingSequence.add(Pair.of(ChannelId.of(2, 2), BFTUpdate.class));

		// Delayed initial Vote from node 0 to (then) next leader 2
		processingSequence.add(Pair.of(ChannelId.of(0, 2), Vote.class));

		DeterministicTest.builder()
			.numNodes(numNodes)
			.messageSelector(sequenceSelector(processingSequence))
			.messageMutator(delayMessagesForNode(0))
			.build()
			.runForCount(processingSequence.size());
	}

	private static MessageSelector sequenceSelector(LinkedList<Pair<ChannelId, Class<?>>> processingSequence) {
		return messages -> {
			if (processingSequence.isEmpty()) {
				// We are finished.
				return null;
			}
			final Pair<ChannelId, Class<?>> messageDetails = processingSequence.pop();
			final ChannelId channel = messageDetails.getFirst();
			final Class<?> msgClass = messageDetails.getSecond();
			for (ControlledMessage message : messages) {
				if (channel.equals(message.channelId())
					&& msgClass.isAssignableFrom(message.message().getClass())) {
						return message;
				}
			}
			fail(String.format("Can't find %s message %s: %s", msgClass.getSimpleName(), channel, messages));
			return null; // Not required, but compiler can't tell that fail throws exception
		};
	}

	private static MessageMutator delayMessagesForNode(int index) {
		return (message, queue) -> {
			final int receiverIndex = message.channelId().receiverIndex();
			final int senderIndex = message.channelId().senderIndex();
			// Delay non-local messages to victim
			final long additionalDelay = (receiverIndex == index && senderIndex != index)
				? 200L : 0L;
			queue.add(message.withAdditionalDelay(additionalDelay));
			return true;
		};
	}
}
