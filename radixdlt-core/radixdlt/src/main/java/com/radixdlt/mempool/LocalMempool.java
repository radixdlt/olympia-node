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
package com.radixdlt.mempool;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.Command;

import javax.annotation.concurrent.GuardedBy;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Iterator;
import java.util.Set;

/**
 * Local-only mempool.
 * <p>
 * Performs no validation and does not share contents with
 * network.  Threadsafe.
 */
public final class LocalMempool implements Mempool {
	private final Object lock = new Object();
	@GuardedBy("lock")
	private final LinkedHashMap<HashCode, Command> data = Maps.newLinkedHashMap();

	private final int maxSize;

	private final Hasher hasher;

	private final EventDispatcher<MempoolAddedCommand> mempoolAddedCommandEventDispatcher;

	private final SystemCounters counters;

	public LocalMempool(
		int maxSize,
		Hasher hasher,
        SystemCounters counters,
        EventDispatcher<MempoolAddedCommand> mempoolAddedCommandEventDispatcher
	) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("mempool.maxSize must be positive: " + maxSize);
		}
		this.maxSize = maxSize;
		this.hasher = hasher;
		this.mempoolAddedCommandEventDispatcher = Objects.requireNonNull(mempoolAddedCommandEventDispatcher);
		this.counters = Objects.requireNonNull(counters);
	}

	@Override
	public void add(Command command) throws MempoolFullException, MempoolDuplicateException {
		synchronized (this.lock) {
			if (this.data.size() >= this.maxSize) {
				throw new MempoolFullException(command, String.format("Mempool full: %s of %s items", this.data.size(), this.maxSize));
			}
			if (null != this.data.put(hasher.hash(command), command)) {
				throw new MempoolDuplicateException(command, String.format("Mempool already has command %s", hasher.hash(command)));
			}
		}

		updateCounts();
		mempoolAddedCommandEventDispatcher.dispatch(MempoolAddedCommand.create(command));
	}

	@Override
	public void removeCommitted(HashCode cmdHash) {
		synchronized (this.lock) {
			this.data.remove(cmdHash);
		}
		updateCounts();
	}

	@Override
	public void removeRejected(HashCode cmdHash) {
		// For now we just treat this the same as committed atoms.
		// Once we have a more complete mempool implementation, we
		// can use this to remove dependent atoms too.
		removeCommitted(cmdHash);
	}

	@Override
	public List<Command> getCommands(int count, Set<HashCode> seen) {
		synchronized (this.lock) {
			int size = Math.min(count, this.data.size());
			if (size > 0) {
				List<Command> commands = Lists.newArrayList();
				Iterator<Command> i = this.data.values().iterator();
				while (commands.size() < size && i.hasNext()) {
					Command a = i.next();
					if (seen.add(hasher.hash(a))) {
						commands.add(a);
					}
				}
				return commands;
			} else {
				return Collections.emptyList();
			}
		}
	}

	@Override
	public int count() {
		synchronized (this.lock) {
			return this.data.size();
		}
	}

	private void updateCounts() {
		this.counters.set(SystemCounters.CounterType.MEMPOOL_COUNT, count());
		this.counters.set(SystemCounters.CounterType.MEMPOOL_MAXCOUNT, this.maxSize);
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
			getClass().getSimpleName(), System.identityHashCode(this), count(), this.maxSize);
	}
}
