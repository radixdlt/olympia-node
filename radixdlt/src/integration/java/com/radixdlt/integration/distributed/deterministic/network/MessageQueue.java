/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.deterministic.network;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Queue for messages by view.
 */
public final class MessageQueue {

	private final HashMap<MessageRank, LinkedList<ControlledMessage>> rankedMessages = Maps.newHashMap();
	private MessageRank minimumMessageRank = null; // Cached minimum view

	MessageQueue() {
		// Nothing here for now
	}

	public boolean add(MessageRank msgRank, ControlledMessage item) {
		this.rankedMessages.computeIfAbsent(msgRank, k -> Lists.newLinkedList()).add(item);
		if (this.minimumMessageRank == null || msgRank.compareTo(this.minimumMessageRank) < 0) {
			this.minimumMessageRank = msgRank;
		}
		return true;
	}

	void remove(ControlledMessage message) {
		LinkedList<ControlledMessage> msgs = this.rankedMessages.get(this.minimumMessageRank);
		if (msgs == null) {
			painfulRemove(message);
			return;
		}
		if (!msgs.remove(message)) {
			painfulRemove(message);
			return;
		}
		if (msgs.isEmpty()) {
			this.rankedMessages.remove(this.minimumMessageRank);
			this.minimumMessageRank = minimumKey(this.rankedMessages.keySet());
		}
	}

	public void dump(PrintStream out) {
		Comparator<ChannelId> channelIdComparator = Comparator
			.<ChannelId>comparingInt(ChannelId::senderIndex)
			.thenComparing(ChannelId::receiverIndex);
		out.println("{");
		this.rankedMessages.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEachOrdered(e1 -> {
				out.format("    %s {%n", e1.getKey());
				e1.getValue().stream()
					.sorted(Comparator.comparing(ControlledMessage::channelId, channelIdComparator))
					.forEach(cm -> out.format("        %s%n", cm));
				out.println("    }");
			});
		out.println("}");
	}

	List<ControlledMessage> lowestRankMessages() {
		if (this.rankedMessages.isEmpty()) {
			return Collections.emptyList();
		}
		return this.rankedMessages.get(this.minimumMessageRank);
	}

	@Override
	public String toString() {
		return this.rankedMessages.toString();
	}

	// If not removing message of the lowest rank, then we do it the painful way
	private void painfulRemove(ControlledMessage message) {
		List<Map.Entry<MessageRank, LinkedList<ControlledMessage>>> entries = Lists.newArrayList(this.rankedMessages.entrySet());
		Collections.sort(entries, Map.Entry.comparingByKey());
		for (Map.Entry<MessageRank, LinkedList<ControlledMessage>> entry : entries) {
			LinkedList<ControlledMessage> msgs = entry.getValue();
			if (msgs != null && msgs.remove(message)) {
				if (msgs.isEmpty()) {
					this.rankedMessages.remove(entry.getKey());
					// Can't affect minimumView if we are here
				}
				return;
			}
		}
		throw new NoSuchElementException();
	}

	// Believe it or not, this is faster, when coupled with minimumView
	// caching, than using a TreeMap for nodes == 100.
	private static MessageRank minimumKey(Set<MessageRank> eavs) {
		if (eavs.isEmpty()) {
			return null;
		}
		List<MessageRank> ranks = Lists.newArrayList(eavs);
		Collections.sort(ranks);
		return ranks.get(0);
	}
}
