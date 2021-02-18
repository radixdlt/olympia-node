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

import javax.annotation.concurrent.GuardedBy;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.utils.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * Local-only mempool.
 * <p>
 * Performs no validation and does not share contents with
 * network.  Threadsafe.
 */
public final class LocalMempool<T, U> implements Mempool<T, U> {
	private final Object lock = new Object();
	@GuardedBy("lock")
	private final LinkedHashMap<U, T> data = Maps.newLinkedHashMap();

	private final int maxSize;

	private final Function<T, U> functionToKey;

	private final SystemCounters counters;

	private final Random random;

	public LocalMempool(
		@MempoolMaxSize int maxSize,
		Function<T, U> functionToKey,
		SystemCounters counters,
		Random random
	) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("mempool.maxSize must be positive: " + maxSize);
		}
		this.maxSize = maxSize;
		this.functionToKey = functionToKey;
		this.counters = Objects.requireNonNull(counters);
		this.random = Objects.requireNonNull(random);
	}

	@Override
	public void add(T command) throws MempoolFullException, MempoolDuplicateException {
		synchronized (this.lock) {
			if (this.data.size() >= this.maxSize) {
				throw new MempoolFullException(
					String.format("Mempool full: %s of %s items", this.data.size(), this.maxSize)
				);
			}
			if (null != this.data.put(functionToKey.apply(command), command)) {
				throw new MempoolDuplicateException(String.format("Mempool already has command %s", functionToKey.apply(command)));
			}
		}

		updateCounts();
	}

	@Override
	public List<Pair<T, Exception>> committed(List<T> commands) {
		commands.forEach(cmd -> this.data.remove(functionToKey.apply(cmd)));
		updateCounts();
		return List.of();
	}

	@Override
	public void remove(T toRemove) {
	    this.data.remove(functionToKey.apply(toRemove));
		updateCounts();
	}

	@Override
	public List<T> getCommands(int count, Set<U> seen) {
		synchronized (this.lock) {
			int size = Math.min(count, this.data.size());
			if (size > 0) {
				List<T> commands = Lists.newArrayList();
				var values = new ArrayList<>(this.data.values());
				Collections.shuffle(values, random);

				Iterator<T> i = values.iterator();
				while (commands.size() < size && i.hasNext()) {
					T a = i.next();
					if (!seen.contains(functionToKey.apply(a))) {
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
