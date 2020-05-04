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

package com.radixdlt.consensus;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.annotations.Nullable;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;

/**
 * Synchronous queuing mechanism for consensus events which require syncing
 * before being able to effectively process it.
 *
 * A separate queue is created for each node in order to keep the message ordering invariant.
 *
 * This class is NOT thread-safe.
 */
public final class SyncQueues {
	private final ImmutableMap<ECPublicKey, SyncQueue> queues;

	private final SystemCounters counters;

	public SyncQueues(
		Set<ECPublicKey> nodes,
		SystemCounters counters
	) {
		this.queues = nodes.stream().collect(ImmutableMap.toImmutableMap(n -> n, n -> new SyncQueue()));
		this.counters = Objects.requireNonNull(counters);
	}

	class SyncQueue {
		private final LinkedList<RequiresSyncConsensusEvent> queue;

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
		public RequiresSyncConsensusEvent peek(@Nullable Hash vertexId) {
			RequiresSyncConsensusEvent e = queue.peek();

			if (e == null) {
				return null;
			}

			if (vertexId != null && !e.getQC().getProposed().getId().equals(vertexId)) {
				return null;
			}

			return e;
		}

		public void pop() {
			queue.pop();
		}

		boolean checkOrAdd(RequiresSyncConsensusEvent event) {
			if (queue.isEmpty()) {
				return true;
			}

			counters.increment(CounterType.CONSENSUS_EVENTS_QUEUED_INITIAL);
			queue.addLast(event);
			return false;
		}

		public void add(RequiresSyncConsensusEvent event) {
			counters.increment(CounterType.CONSENSUS_EVENTS_QUEUED_SYNC);
			queue.addLast(event);
		}
	}

	ImmutableCollection<SyncQueue> getQueues() {
		return queues.values();
	}

	boolean checkOrAdd(RequiresSyncConsensusEvent event) {
		return queues.get(event.getAuthor()).checkOrAdd(event);
	}

	void add(RequiresSyncConsensusEvent event) {
		queues.get(event.getAuthor()).add(event);
	}

	void clear() {
		queues.values().forEach(q -> q.queue.clear());
	}
}
