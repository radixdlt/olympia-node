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

package com.radixdlt.environment.deterministic.network;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.stream.Collectors;

/**
 * Queue for messages by view.
 */
public final class MessageQueue {

	private final HashMap<Long, LinkedList<ControlledMessage>> messagesByTime = Maps.newHashMap();
	private long minimumMessageTime = Long.MAX_VALUE; // Cached minimum time

	MessageQueue() {
		// Nothing here for now
	}

	public boolean add(ControlledMessage item) {
		long messageTime = item.arrivalTime();
		this.messagesByTime.computeIfAbsent(messageTime, k -> Lists.newLinkedList()).add(item);
		if (messageTime < this.minimumMessageTime) {
			this.minimumMessageTime = messageTime;
		}
		return true;
	}

	public boolean addFirst(ControlledMessage item) {
		long messageTime = item.arrivalTime();
		this.messagesByTime.computeIfAbsent(messageTime, k -> Lists.newLinkedList()).addFirst(item);
		if (messageTime < this.minimumMessageTime) {
			this.minimumMessageTime = messageTime;
		}
		return true;
	}

	public boolean addBefore(ControlledMessage item, Predicate<ControlledMessage> test) {
		var messageTime = item.arrivalTime();
		var i = this.messagesByTime.computeIfAbsent(messageTime, k -> Lists.newLinkedList()).listIterator();
		var inserted = false;
		while (i.hasNext()) {
			if (test.test(i.next())) {
				// Backup and insert
				i.previous();
				i.add(item);
				inserted = true;
				break;
			}
		}
		if (!inserted) {
			i.add(item);
		}
		if (messageTime < this.minimumMessageTime) {
			this.minimumMessageTime = messageTime;
		}
		return true;
	}

	void remove(Predicate<ControlledMessage> filter) {
		messagesByTime.values().forEach(l -> l.removeIf(filter));
		messagesByTime.values().removeIf(List::isEmpty);
		this.minimumMessageTime = minimumKey(this.messagesByTime.keySet());
	}

	void remove(ControlledMessage message) {
		LinkedList<ControlledMessage> msgs = this.messagesByTime.get(this.minimumMessageTime);
		if (msgs == null) {
			painfulRemove(message);
			return;
		}
		if (!msgs.remove(message)) {
			painfulRemove(message);
			return;
		}
		if (msgs.isEmpty()) {
			this.messagesByTime.remove(this.minimumMessageTime);
			this.minimumMessageTime = minimumKey(this.messagesByTime.keySet());
		}
	}

	public void dump(PrintStream out) {
		Comparator<ChannelId> channelIdComparator = Comparator
			.<ChannelId>comparingInt(ChannelId::senderIndex)
			.thenComparing(ChannelId::receiverIndex);
		out.println("{");
		this.messagesByTime.entrySet().stream()
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

	Set<ControlledMessage> allMessages() {
		return this.messagesByTime.values().stream()
			.flatMap(LinkedList::stream)
			.collect(Collectors.toSet());
	}

	List<ControlledMessage> lowestTimeMessages() {
		if (this.messagesByTime.isEmpty()) {
			return Collections.emptyList();
		}
		return this.messagesByTime.get(this.minimumMessageTime);
	}

	@Override
	public String toString() {
		return this.messagesByTime.toString();
	}

	// If not removing message of the lowest rank, then we do it the painful way
	private void painfulRemove(ControlledMessage message) {
		List<Map.Entry<Long, LinkedList<ControlledMessage>>> entries = Lists.newArrayList(this.messagesByTime.entrySet());
		Collections.sort(entries, Map.Entry.comparingByKey());
		for (Map.Entry<Long, LinkedList<ControlledMessage>> entry : entries) {
			LinkedList<ControlledMessage> msgs = entry.getValue();
			if (msgs != null && msgs.remove(message)) {
				if (msgs.isEmpty()) {
					this.messagesByTime.remove(entry.getKey());
					// Can't affect minimumView if we are here
				}
				return;
			}
		}
		throw new NoSuchElementException();
	}

	// Believe it or not, this is faster, when coupled with minimumView
	// caching, than using a TreeMap for nodes == 100.
	private static long minimumKey(Set<Long> eavs) {
		if (eavs.isEmpty()) {
			return Long.MAX_VALUE;
		}
		return Collections.min(eavs);
	}
}
