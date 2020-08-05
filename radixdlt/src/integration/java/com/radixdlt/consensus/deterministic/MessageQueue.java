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

package com.radixdlt.consensus.deterministic;

import java.io.PrintStream;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ChannelId;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ControlledMessage;
import com.radixdlt.consensus.deterministic.ControlledNetwork.MessageRank;

/**
 * Queue for messages by view.
 */
final class MessageQueue {
	// Should really be an AbstractSequentialList, but I don't really want to have
	// to implement a ListIterator.  Performance seems adequate for now.
	final class ArrayOfLinkedLists<T> extends AbstractList<T> {

		private final Object[] lists;
		private final int size;

		ArrayOfLinkedLists(Collection<LinkedList<T>> lists) {
			this.lists = lists.toArray();
			int count = 0;
			for (int i = 0; i < this.lists.length; ++i) {
				count += list(i).size();
			}
			this.size = count;
		}

		@SuppressWarnings("unchecked")
		private LinkedList<T> list(int i) {
			return (LinkedList<T>) lists[i];
		}

		@Override
		public T get(int index) {
			int currentIndex = index;
			for (int i = 0; i < this.lists.length; ++i) {
				LinkedList<T> list = list(i);
				int listSize = list.size();
				if (currentIndex < listSize) {
					return list.get(currentIndex);
				} else {
					currentIndex -= listSize;
				}
			}
			throw new ArrayIndexOutOfBoundsException(index);
		}

		@Override
		public int size() {
			return this.size;
		}
	}

	private final HashMap<MessageRank, HashMap<ChannelId, LinkedList<ControlledMessage>>> messageQueue = Maps.newHashMap();
	private MessageRank minimumMessageRank = null; // Cached minimum view

	MessageQueue() {
		// Nothing here for now
	}

	void add(MessageRank msgRank, ControlledMessage item) {
		this.messageQueue.computeIfAbsent(msgRank, k -> Maps.newHashMap()).computeIfAbsent(item.getChannelId(), k -> Lists.newLinkedList()).add(item);
		if (this.minimumMessageRank == null || msgRank.compareTo(this.minimumMessageRank) < 0) {
			this.minimumMessageRank = msgRank;
		}
	}

	ControlledMessage pop(ChannelId channelId) {
		HashMap<ChannelId, LinkedList<ControlledMessage>> msgMap = this.messageQueue.get(this.minimumMessageRank);
		if (msgMap == null) {
			return painfulPop(channelId);
		}
		LinkedList<ControlledMessage> msgs = msgMap.get(channelId);
		if (msgs == null) {
			return painfulPop(channelId);
		}
		ControlledMessage item = msgs.pop();
		if (msgs.isEmpty()) {
			msgMap.remove(channelId);
			if (msgMap.isEmpty()) {
				this.messageQueue.remove(this.minimumMessageRank);
				this.minimumMessageRank = minimumKey(this.messageQueue.keySet());
			}
		}
		return item;
	}

	// Really only here to work with processNextMsg(int, int, Class<?>) in BFTDeterministicTest
	private ControlledMessage painfulPop(ChannelId channelId) {
		List<Map.Entry<MessageRank, HashMap<ChannelId, LinkedList<ControlledMessage>>>> entries = Lists.newArrayList(this.messageQueue.entrySet());
		Collections.sort(entries, Map.Entry.comparingByKey());
		for (Map.Entry<MessageRank, HashMap<ChannelId, LinkedList<ControlledMessage>>> entry : entries) {
			HashMap<ChannelId, LinkedList<ControlledMessage>> msgMap = entry.getValue();
			LinkedList<ControlledMessage> msgs = msgMap.get(channelId);
			if (msgs != null) {
				ControlledMessage item = msgs.pop();
				if (msgs.isEmpty()) {
					msgMap.remove(channelId);
					if (msgMap.isEmpty()) {
						this.messageQueue.remove(entry.getKey());
						// Can't affect minimumView if we are here
					}
				}
				return item;
			}
		}
		throw new NoSuchElementException();
	}

	List<ControlledMessage> lowestViewMessages() {
		if (this.messageQueue.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayOfLinkedLists<>(this.messageQueue.get(this.minimumMessageRank).values());
	}

	@Override
	public String toString() {
		return this.messageQueue.toString();
	}

	// Believe it or not, this is faster, when coupled with minimumView
	// caching, than using a TreeMap for nodes == 100.
	private static MessageRank minimumKey(Set<MessageRank> eavs) {
		if (eavs.isEmpty()) {
			return null;
		}
		List<MessageRank> views = Lists.newArrayList(eavs);
		Collections.sort(views);
		return views.get(0);
	}

	void dump(PrintStream out, Function<BFTNode, Object> namer) {
		Comparator<ChannelId> channelIdComparator = Comparator
			.<ChannelId, String>comparing(chid -> chid.getSender().getSimpleName())
			.thenComparing(chid -> chid.getReceiver().getSimpleName());
		out.println("{");
		this.messageQueue.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEachOrdered(e1 -> {
				out.format("    %s {%n", e1.getKey());
				e1.getValue().entrySet().stream()
					.sorted(Map.Entry.comparingByKey(channelIdComparator))
					.forEach(e2 -> out.format("        %s: %s%n", nameChannel(e2.getKey(), namer), e2.getValue()));
				out.println("    }");
			});
		out.println("}");
	}

	private String nameChannel(ChannelId ch, Function<BFTNode, Object> namer) {
		return String.format("%s->%s", namer.apply(ch.getSender()), namer.apply(ch.getReceiver()));
	}
}
