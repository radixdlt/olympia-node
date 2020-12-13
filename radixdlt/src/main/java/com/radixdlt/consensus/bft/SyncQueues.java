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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.ConsensusEvent;
import com.google.common.hash.HashCode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/**
 * Synchronous queuing mechanism for consensus events which require syncing
 * before being able to effectively process it.
 *
 * A separate queue is created for each node in order to keep the message ordering invariant.
 *
 * This class is NOT thread-safe.
 */
public final class SyncQueues {
	private final Map<BFTNode, SyncQueue> queues;

	SyncQueues() {
		this.queues = new HashMap<>();
	}

	class SyncQueue {
		private final LinkedList<ConsensusEvent> queue;

		private SyncQueue() {
			this.queue = new LinkedList<>();
		}

		/**
		 * If a vertexId is supplied, checks the top of the queue to see if
		 * the event corresponds to the vertexId. If so, returns it.
		 *
		 * TODO: cleanup interfaces
		 *
		 * @param vertexId the vertexId to check, if null, no vertexId is checked
		 * @return the top of the queue if requirements are met
		 */
		@Nullable
		public ConsensusEvent peek(@Nullable HashCode vertexId) {
			ConsensusEvent e = queue.peek();

			if (e == null) {
				return null;
			}

			if (vertexId != null && !e.highQC().highestQC().getProposed().getVertexId().equals(vertexId)) {
				return null;
			}

			return e;
		}

		public void pop() {
			queue.pop();
		}

		boolean isEmptyElseAdd(ConsensusEvent event) {
			if (queue.isEmpty()) {
				return true;
			}
			queue.addLast(event);
			return false;
		}

		boolean isEmpty() {
			return queue.isEmpty();
		}

		public void add(ConsensusEvent event) {
			queue.addLast(event);
		}

		public ConsensusEvent clearViewAndGetNext(View view) {
			Iterator<ConsensusEvent> eventsIterator = queue.iterator();
			while (eventsIterator.hasNext()) {
				ConsensusEvent event = eventsIterator.next();
				if (event.getView().compareTo(view) <= 0) {
					eventsIterator.remove();
				} else {
					return event;
				}
			}

			return null;
		}

		@Override
		public String toString() {
			return queue.toString();
		}
	}

	Collection<SyncQueue> getQueues() {
		return queues.values();
	}

	void add(ConsensusEvent event) {
		queues.computeIfAbsent(event.getAuthor(), a -> new SyncQueue()).add(event);
	}

	@Override
	public String toString() {
		final StringJoiner joiner = new StringJoiner(",");
		queues.forEach((node, queue) -> {
			if (!queue.queue.isEmpty()) {
				joiner.add(String.format("%s=%s", node, queue));
			}
		});
		return String.format("{%s}", joiner);
	}
}
